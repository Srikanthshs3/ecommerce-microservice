package org.praskanth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One line of the tracking timeline ("Packed by the seller", "Out for delivery" ...). Append-only: it is the audit trail. */
@Entity
@Table(name = "shipment_events", indexes = @Index(name = "idx_shipment_events_order", columnList = "order_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(length = 500)
    private String note;

    /** Who did it: SYSTEM, SELLER #4, CUSTOMER #7, AGENT #9 ... */
    @Column(length = 60)
    private String actor;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
