package org.praskanth.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ShipmentDtos {
    private ShipmentDtos() {}

    public record TimelineEntry(String status, String note, String actor, Instant at) {}

    /** deliveryCode is filled ONLY for the customer who owns the order, and only while the parcel is out for delivery. */
    public record ShipmentView(Long orderId, String trackingNumber, String carrier, String status,
                               String paymentMethod, BigDecimal cashToCollect, String shippingAddress,
                               boolean sellerCanPack, boolean cancellable, Instant createdAt, Instant updatedAt,
                               List<TimelineEntry> timeline, String deliveryCode, Long agentId, int failedAttempts,
                               Boolean cashCollected) {}

    /** What an agent sees in the pool BEFORE taking a parcel: the area only, never the full address. */
    public record PoolItem(Long orderId, String trackingNumber, String area, String paymentMethod,
                           BigDecimal cashToCollect, int failedAttempts, Instant readySince) {}

    public record DeliverRequest(String otp, Boolean cashCollected) {}

    public record FailRequest(String reason) {}

    public record CancelRequest(String reason) {}
}
