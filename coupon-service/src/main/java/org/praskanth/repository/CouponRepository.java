package org.praskanth.repository;

import jakarta.persistence.LockModeType;
import org.praskanth.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    /** Row lock: redemptions of one coupon are processed one at a time, so usage limits can never be overshot. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.code = :code")
    Optional<Coupon> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") Long id);

    List<Coupon> findAllByOrderByCreatedAtDesc();

    List<Coupon> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    /** Public (listed) coupons that are switched on, inside their dates and not used up. */
    @Query("""
            select c from Coupon c
            where c.active = true and c.listed = true
              and c.validFrom <= :now
              and (c.validUntil is null or c.validUntil >= :now)
              and (c.usageLimit is null or c.usedCount < c.usageLimit)
            order by c.createdAt desc
            """)
    List<Coupon> findAvailable(@Param("now") Instant now);
}
