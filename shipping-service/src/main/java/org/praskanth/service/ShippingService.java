package org.praskanth.service;

import org.praskanth.dto.ShipmentResponse;
import org.praskanth.entity.Shipment;
import org.praskanth.entity.ShipmentStatus;
import org.praskanth.kafka.ShipmentEventProducer;
import org.praskanth.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/** Terminal happy-path Saga step, triggered by PaymentCompletedEvent. Tracking number generation is simulated - swap for a real carrier API integration (FedEx/UPS/Shiprocket/etc.) when ready. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingService {

    private static final String DEFAULT_CARRIER = "SimulatedCarrier";

    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventProducer shipmentEventProducer;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Idempotent at the DB level (unique constraint on order_id) as a second guard alongside the Redis dedup in the consumer. */
    @Transactional
    public void createShipmentForOrder(Long orderId) {
        if (shipmentRepository.existsByOrderId(orderId)) {
            log.info("Shipment already recorded for order {}, skipping (idempotent no-op)", orderId);
            return;
        }

        String trackingNumber = generateTrackingNumber();

        Shipment shipment = Shipment.builder()
                .orderId(orderId)
                .trackingNumber(trackingNumber)
                .carrier(DEFAULT_CARRIER)
                .status(ShipmentStatus.CREATED)
                .build();

        shipmentRepository.save(shipment);
        shipmentEventProducer.publishShipmentCreated(orderId, trackingNumber, DEFAULT_CARRIER);
        log.info("Shipment created for order {} tracking {}", orderId, trackingNumber);
    }

    public ShipmentResponse getByOrderId(Long orderId) {
        return shipmentRepository.findByOrderId(orderId).map(this::toResponse).orElse(null);
    }

    private String generateTrackingNumber() {
        return "TRK" + (100000000 + secureRandom.nextInt(900000000));
    }

    private ShipmentResponse toResponse(Shipment shipment) {
        return ShipmentResponse.builder()
                .orderId(shipment.getOrderId())
                .trackingNumber(shipment.getTrackingNumber())
                .carrier(shipment.getCarrier())
                .status(shipment.getStatus().name())
                .createdAt(shipment.getCreatedAt())
                .build();
    }
}
