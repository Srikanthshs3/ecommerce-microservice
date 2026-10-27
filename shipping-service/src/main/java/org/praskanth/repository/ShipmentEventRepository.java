package org.praskanth.repository;

import org.praskanth.entity.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, Long> {
    List<ShipmentEvent> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);
}
