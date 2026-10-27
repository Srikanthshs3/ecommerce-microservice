package org.praskanth.repository;

import org.praskanth.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
    Optional<PaymentAllocation> findByOrderId(Long orderId);
}
