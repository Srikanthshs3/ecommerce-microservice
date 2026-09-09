package org.praskanth.repository;

import org.praskanth.entity.Order;
import org.praskanth.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);

    List<Order> findBySellerId(Long sellerId);

    List<Order> findBySellerIdAndStatus(Long sellerId, OrderStatus orderStatus);
}
