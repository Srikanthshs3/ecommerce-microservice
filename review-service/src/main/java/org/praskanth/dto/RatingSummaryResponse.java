package org.praskanth.dto;

import java.util.Map;

/**
 * @param averageRating one decimal, 0.0 when there are no reviews
 * @param distribution  star -> number of reviews, always has keys 5,4,3,2,1 (highest first)
 */
public record RatingSummaryResponse(String productId, double averageRating, long totalReviews, Map<Integer, Long> distribution) {
}
