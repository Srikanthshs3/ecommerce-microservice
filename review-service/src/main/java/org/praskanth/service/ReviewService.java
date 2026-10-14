package org.praskanth.service;

import lombok.extern.slf4j.Slf4j;
import org.praskanth.config.OrderApiClient;
import org.praskanth.config.ProductApiClient;
import org.praskanth.config.ReviewProperties;
import org.praskanth.config.UserApiClient;
import org.praskanth.dto.*;
import org.praskanth.entity.Review;
import org.praskanth.entity.ReviewImage;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.exception.ServiceUnavailableException;
import org.praskanth.repository.ReviewRepository;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.storage.ImageStorage;
import org.praskanth.storage.ImageValidator;
import org.praskanth.storage.ImageValidator.DetectedImage;
import org.praskanth.storage.StoredImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Review rules:
 *  - only CUSTOMERs write reviews, once per product (editing replaces it), never on their own product;
 *  - 1..5 stars, optional title / comment, up to {@code review.images.max-count} pictures;
 *  - "Verified purchase" when order-service shows a paid order containing the product
 *    (and, if review.require-purchase=true, a review without one is refused).
 *
 * Pictures are files, the DB is a transaction: the rule is "never leave a review pointing at a missing file, and
 * never leave a file nobody points at". New files are deleted if the transaction rolls back; replaced / removed
 * files are deleted only after the commit.
 */
@Service
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ImageStorage imageStorage;
    private final ImageValidator imageValidator;
    private final ProductApiClient productApiClient;
    private final OrderApiClient orderApiClient;
    private final UserApiClient userApiClient;
    private final ReviewProperties properties;
    private final TransactionTemplate tx;

    public ReviewService(ReviewRepository reviewRepository, ImageStorage imageStorage, ImageValidator imageValidator,
                         ProductApiClient productApiClient, OrderApiClient orderApiClient, UserApiClient userApiClient,
                         ReviewProperties properties, PlatformTransactionManager transactionManager) {
        this.reviewRepository = reviewRepository;
        this.imageStorage = imageStorage;
        this.imageValidator = imageValidator;
        this.productApiClient = productApiClient;
        this.orderApiClient = orderApiClient;
        this.userApiClient = userApiClient;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // =====================================================================================================
    //  Write
    // =====================================================================================================

    /**
     * Deliberately NOT @Transactional: it makes HTTP calls to other services and writes files, and a DB connection
     * must not be held open meanwhile. Only the final save runs in a transaction.
     */
    public ReviewResponse create(GatewayUser user, CreateReviewRequest request, List<MultipartFile> images) {
        List<MultipartFile> files = nonEmpty(images);
        List<DetectedImage> detected = imageValidator.validateAll(files, properties.getImages().getMaxCount());

        String productId = request.getProductId().trim();
        Long sellerId = productApiClient.getProduct(productId).getSellerId();
        if (sellerId != null && sellerId.equals(user.id())) {
            throw new ForbiddenOperationException("You cannot review your own product.");
        }
        if (reviewRepository.existsByProductIdAndUserId(productId, user.id())) {
            throw new ConflictException("You have already reviewed this product. Edit your existing review instead.");
        }

        boolean verified = isVerifiedPurchase(user.id(), productId);
        if (properties.isRequirePurchase() && !verified) {
            throw new ForbiddenOperationException("Only customers who bought this product can review it.");
        }
        String userName = userApiClient.displayName(user);

        List<StoredImage> stored = storeAll(files, detected);
        try {
            Review saved = tx.execute(status -> {
                Review review = new Review();
                review.setProductId(productId);
                review.setUserId(user.id());
                review.setUserName(userName);
                review.setRating(request.getRating());
                review.setTitle(blankToNull(request.getTitle()));
                review.setComment(blankToNull(request.getComment()));
                review.setVerifiedPurchase(verified);
                stored.forEach(s -> review.addImage(new ReviewImage(s.fileName(), s.contentType(), s.sizeBytes())));
                return reviewRepository.saveAndFlush(review);
            });
            log.info("Review {} created for product {} by user {} ({} pictures)", saved.getId(), productId, user.id(), stored.size());
            return ReviewResponse.from(saved, user.id());
        } catch (RuntimeException e) {
            stored.forEach(s -> imageStorage.delete(s.fileName())); // nothing points at these files
            throw e;
        }
    }

    @Transactional
    public ReviewResponse update(GatewayUser user, Long reviewId, UpdateReviewRequest request, List<MultipartFile> images) {
        Review review = findOrThrow(reviewId);
        if (!review.getUserId().equals(user.id())) {
            throw new ForbiddenOperationException("You can only edit your own review.");
        }

        List<MultipartFile> files = nonEmpty(images);
        Set<Long> removeIds = request.getRemoveImageIds() == null ? Set.of() : new HashSet<>(request.getRemoveImageIds());
        List<ReviewImage> toRemove = review.getImages().stream().filter(i -> removeIds.contains(i.getId())).toList();
        if (toRemove.size() != removeIds.size()) {
            throw new BadRequestException("Some pictures to remove do not belong to this review.");
        }

        int slotsLeft = properties.getImages().getMaxCount() - (review.getImages().size() - toRemove.size());
        List<DetectedImage> detected = imageValidator.validateAll(files, slotsLeft);

        List<StoredImage> stored = storeAll(files, detected);
        deleteOnRollback(stored.stream().map(StoredImage::fileName).toList());

        review.setRating(request.getRating());
        review.setTitle(blankToNull(request.getTitle()));
        review.setComment(blankToNull(request.getComment()));

        List<String> removedFiles = toRemove.stream().map(ReviewImage::getFileName).toList();
        review.getImages().removeAll(toRemove); // orphanRemoval deletes the rows
        stored.forEach(s -> review.addImage(new ReviewImage(s.fileName(), s.contentType(), s.sizeBytes())));
        renumber(review);

        Review saved = reviewRepository.saveAndFlush(review);
        deleteAfterCommit(removedFiles);
        return ReviewResponse.from(saved, user.id());
    }

    @Transactional
    public void delete(GatewayUser user, Long reviewId) {
        Review review = findOrThrow(reviewId);
        if (!review.getUserId().equals(user.id()) && !user.isAdmin()) {
            throw new ForbiddenOperationException("You can only delete your own review.");
        }
        List<String> files = review.getImages().stream().map(ReviewImage::getFileName).toList();
        reviewRepository.delete(review);
        deleteAfterCommit(files);
        log.info("Review {} deleted by user {}", reviewId, user.id());
    }

    // =====================================================================================================
    //  Read
    // =====================================================================================================

    @Transactional(readOnly = true)
    public ReviewPageResponse list(String productId, int page, int size, String sort, Integer rating, Long callerId) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BadRequestException("rating must be between 1 and 5");
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), sortFor(sort));
        Page<Review> result = rating == null
                ? reviewRepository.findByProductId(productId, pageable)
                : reviewRepository.findByProductIdAndRating(productId, rating, pageable);
        return new ReviewPageResponse(
                result.getContent().stream().map(r -> ReviewResponse.from(r, callerId)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ReviewResponse myReview(String productId, Long userId) {
        return reviewRepository.findByProductIdAndUserId(productId, userId)
                .map(r -> ReviewResponse.from(r, userId))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public RatingSummaryResponse summary(String productId) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            distribution.put(star, 0L);
        }
        long total = 0;
        long weighted = 0;
        for (Object[] row : reviewRepository.countByRating(productId)) {
            int star = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            distribution.put(star, count);
            total += count;
            weighted += (long) star * count;
        }
        return new RatingSummaryResponse(productId, total == 0 ? 0.0 : round1(weighted / (double) total), total, distribution);
    }

    /** Ratings for many products in ONE query (the product grid). Products without reviews are included with 0 / 0. */
    @Transactional(readOnly = true)
    public List<ProductRatingResponse> summaries(Collection<String> productIds) {
        Map<String, ProductRatingResponse> found = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (Object[] row : reviewRepository.summarize(productIds)) {
                String id = (String) row[0];
                found.put(id, new ProductRatingResponse(id, round1(((Number) row[1]).doubleValue()), ((Number) row[2]).longValue()));
            }
        }
        return productIds.stream()
                .map(id -> found.getOrDefault(id, new ProductRatingResponse(id, 0.0, 0)))
                .toList();
    }

    // =====================================================================================================
    //  helpers
    // =====================================================================================================

    private boolean isVerifiedPurchase(Long userId, String productId) {
        try {
            return orderApiClient.hasPurchased(userId, productId);
        } catch (Exception e) {
            if (properties.isRequirePurchase()) {
                throw new ServiceUnavailableException("Could not check your purchase history right now. Please try again shortly.", e);
            }
            log.warn("Purchase check failed for user {} / product {}: {} (review saved as unverified)", userId, productId, e.getMessage());
            return false;
        }
    }

    private List<StoredImage> storeAll(List<MultipartFile> files, List<DetectedImage> detected) {
        List<StoredImage> stored = new ArrayList<>(files.size());
        try {
            for (int i = 0; i < files.size(); i++) {
                stored.add(imageStorage.store(files.get(i), detected.get(i).extension(), detected.get(i).contentType()));
            }
        } catch (RuntimeException e) {
            stored.forEach(s -> imageStorage.delete(s.fileName()));
            throw e;
        }
        return stored;
    }

    private void deleteOnRollback(List<String> fileNames) {
        if (fileNames.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    fileNames.forEach(imageStorage::delete);
                }
            }
        });
    }

    private void deleteAfterCommit(List<String> fileNames) {
        if (fileNames.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileNames.forEach(imageStorage::delete);
            }
        });
    }

    private static void renumber(Review review) {
        int position = 0;
        for (ReviewImage image : review.getImages()) {
            image.setPosition(position++);
        }
    }

    private Review findOrThrow(Long id) {
        return reviewRepository.findById(id).orElseThrow(() -> new NotFoundException("Review not found: " + id));
    }

    private static Sort sortFor(String sort) {
        Sort newest = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        if (sort == null) {
            return newest;
        }
        return switch (sort.toLowerCase(Locale.ROOT)) {
            case "highest" -> Sort.by(Sort.Direction.DESC, "rating").and(newest);
            case "lowest" -> Sort.by(Sort.Direction.ASC, "rating").and(newest);
            default -> newest; // "recent"
        };
    }

    private static List<MultipartFile> nonEmpty(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        // Browsers send one empty part when no file was chosen; ignore those.
        return files.stream().filter(f -> f != null && !(f.isEmpty() && (f.getOriginalFilename() == null || f.getOriginalFilename().isEmpty()))).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
