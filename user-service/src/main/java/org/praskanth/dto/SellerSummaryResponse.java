package org.praskanth.dto;

import java.time.Instant;

/** Lightweight row for admin lists - no lazy associations are touched. */
public record SellerSummaryResponse(Long id, String storeName, String legalName, String gstin,
                                    String kycStatus, Instant createdAt) {}
