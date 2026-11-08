package org.praskanth.repository;

import org.praskanth.entity.Shipment;
import org.praskanth.entity.ShipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    Optional<Shipment> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    List<Shipment> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    List<Shipment> findTop50ByStatusOrderByUpdatedAtAsc(ShipmentStatus status);

    List<Shipment> findTop50ByStatusAndShippingPincodeInOrderByUpdatedAtAsc(ShipmentStatus status, Collection<String> pincodes);

    long countByAgentIdAndStatus(Long agentId, ShipmentStatus status);

    List<Shipment> findByAgentIdAndStatusOrderByPickedUpAtDesc(Long agentId, ShipmentStatus status);

    List<Shipment> findTop30ByAgentIdAndStatusOrderByDeliveredAtDesc(Long agentId, ShipmentStatus status);

    List<Shipment> findBySellerIdAndStatusInOrderByCreatedAtDesc(Long sellerId, Collection<ShipmentStatus> statuses);
}
