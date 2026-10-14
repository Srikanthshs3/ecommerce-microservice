package org.praskanth.dto;

import java.util.List;

public record ReviewPageResponse(List<ReviewResponse> items, int page, int size, long totalElements, int totalPages) {
}
