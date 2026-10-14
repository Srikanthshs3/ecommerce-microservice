package org.praskanth.dto;

import org.praskanth.entity.Review;

import java.time.Instant;
import java.util.List;

public record ReviewResponse(
        Long id,
        String productId,
        String userName,
        int rating,
        String title,
        String comment,
        boolean verifiedPurchase,
        List<ReviewImageResponse> images,
        Instant createdAt,
        Instant updatedAt,
        /** true when the caller wrote this review (UI shows Edit / Delete) */
        boolean mine) {

    public static final String IMAGE_URL_PREFIX = "/api/v1/review/images/";

    public static ReviewResponse from(Review review, Long callerId) {
        return new ReviewResponse(
                review.getId(),
                review.getProductId(),
                review.getUserName(),
                review.getRating(),
                review.getTitle(),
                review.getComment(),
                review.isVerifiedPurchase(),
                review.getImages().stream()
                        .map(i -> new ReviewImageResponse(i.getId(), IMAGE_URL_PREFIX + i.getFileName()))
                        .toList(),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                callerId != null && callerId.equals(review.getUserId()));
    }
}
