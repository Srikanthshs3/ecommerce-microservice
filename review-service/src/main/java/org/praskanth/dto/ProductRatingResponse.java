package org.praskanth.dto;

/** Lightweight rating for product cards. */
public record ProductRatingResponse(String productId, double averageRating, long totalReviews) {
}
