package org.praskanth.dto;

import java.time.Instant;

/** What shoppers may see about a seller ("Sold by ..."). No compliance or contact data. */
public record SellerPublicResponse(Long sellerId, String storeName, String logo, String banner, Instant memberSince) {}
