package org.praskanth.repository;

import org.praskanth.entity.CouponRedemption;
import org.praskanth.entity.RedemptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    Optional<CouponRedemption> findByCheckoutRef(String checkoutRef);

    long countByCouponIdAndUserIdAndStatus(Long couponId, Long userId, RedemptionStatus status);

    boolean existsByCouponId(Long couponId);
}
