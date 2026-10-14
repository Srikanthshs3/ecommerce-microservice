package org.praskanth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.*;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.ReviewService;
import org.praskanth.storage.ImageStorage;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ImageStorage imageStorage;

    // ---------------------------------------------------------------- write (multipart: JSON part "review" + file parts "images")

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @Valid @RequestPart("review") CreateReviewRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal GatewayUser user) {
        ReviewResponse response = reviewService.create(user, request, images);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Review added", response));
    }

    @PutMapping(value = "/{reviewId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReviewResponse>> update(
            @PathVariable Long reviewId,
            @Valid @RequestPart("review") UpdateReviewRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success("Review updated", reviewService.update(user, reviewId, request, images)));
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long reviewId, @AuthenticationPrincipal GatewayUser user) {
        reviewService.delete(user, reviewId); // owner or ADMIN, checked in the service
        return ResponseEntity.ok(ApiResponse.success("Review deleted", null));
    }

    // ---------------------------------------------------------------- read (public, except /mine)

    /** sort = recent (default) | highest | lowest ; rating = optional 1..5 filter */
    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<ReviewPageResponse>> list(
            @PathVariable String productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(required = false) Integer rating,
            @AuthenticationPrincipal GatewayUser user) {
        Long callerId = user == null ? null : user.id();
        return ResponseEntity.ok(ApiResponse.success(reviewService.list(productId, page, size, sort, rating, callerId)));
    }

    @GetMapping("/product/{productId}/summary")
    public ResponseEntity<ApiResponse<RatingSummaryResponse>> summary(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.summary(productId)));
    }

    /** The caller's own review of this product, or data = null when they have not written one yet. */
    @GetMapping("/product/{productId}/mine")
    public ResponseEntity<ApiResponse<ReviewResponse>> mine(@PathVariable String productId, @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.myReview(productId, user.id())));
    }

    /** /summaries?productIds=a,b,c - star ratings for a whole page of product cards in one call (max 100). */
    @GetMapping("/summaries")
    public ResponseEntity<ApiResponse<List<ProductRatingResponse>>> summaries(@RequestParam List<String> productIds) {
        List<String> ids = productIds.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().limit(100).toList();
        return ResponseEntity.ok(ApiResponse.success(reviewService.summaries(ids)));
    }

    /** Streams one stored picture. File names are random UUIDs, so they can be cached forever. */
    @GetMapping("/images/{fileName}")
    public ResponseEntity<Resource> image(@PathVariable String fileName) {
        Resource resource = imageStorage.load(fileName);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .contentType(mediaTypeOf(fileName))
                .body(resource);
    }

    private static MediaType mediaTypeOf(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}
