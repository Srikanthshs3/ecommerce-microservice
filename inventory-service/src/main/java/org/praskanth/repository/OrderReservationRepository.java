package org.praskanth.repository;

import org.praskanth.entity.OrderReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderReservationRepository extends JpaRepository<OrderReservation, Long> {
    List<OrderReservation> findByOrderIdAndStatus(Long orderId, OrderReservation.ReservationStatus status);
    boolean existsByOrderId(Long orderId);
}
