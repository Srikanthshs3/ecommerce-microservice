package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.CouponLine;
import org.praskanth.dto.CouponResult;
import org.praskanth.dto.RedeemRequest;
import org.praskanth.dto.ValidateRequest;
import org.praskanth.entity.Coupon;
import org.praskanth.entity.CouponRedemption;
import org.praskanth.entity.RedemptionStatus;
import org.praskanth.repository.CouponRedemptionRepository;
import org.praskanth.repository.CouponRepository;
import org.praskanth.security.gateway.GatewayUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The runtime side of coupons, used by cart-service (validate: "what would I save?") and order-service
 * (redeem: "use it for this checkout", release: "give it back").
 *
 * redeem() holds a row lock on the coupon, so two checkouts can never both take the last use, and a customer
 * can't beat a per-user limit by clicking twice. It is idempotent per checkoutRef.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedemptionService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;

    public static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public CouponResult validate(GatewayUser user, ValidateRequest request) {
        String code = normalize(request.code());
        Optional<Coupon> found = couponRepository.findByCode(code);
        if (found.isEmpty()) {
            return CouponResult.invalid(code, "Invalid coupon code.");
        }
        return evaluate(found.get(), user.id(), request.lines());
    }

    @Transactional
    public CouponResult redeem(GatewayUser user, RedeemRequest request) {
        String code = normalize(request.code());

        Optional<CouponRedemption> existing = redemptionRepository.findByCheckoutRef(request.checkoutRef());
        if (existing.isPresent()) { // a retry of a call that already succeeded
            CouponRedemption redemption = existing.get();
            if (redemption.getStatus() != RedemptionStatus.RESERVED) {
                return CouponResult.invalid(code, "This checkout has already been released.");
            }
            Coupon coupon = couponRepository.findById(redemption.getCouponId()).orElseThrow();
            CouponEngine.Result result = CouponEngine.calculate(coupon, request.lines());
            return CouponResult.ok(coupon.getCode(), coupon.getDescription(), redemption.getDiscountAmount(), result.allocations());
        }

        Optional<Coupon> found = couponRepository.findByCodeForUpdate(code);
        if (found.isEmpty()) {
            return CouponResult.invalid(code, "Invalid coupon code.");
        }
        Coupon coupon = found.get();
        CouponResult result = evaluate(coupon, user.id(), request.lines());
        if (!result.valid()) {
            return result;
        }

        CouponRedemption redemption = new CouponRedemption();
        redemption.setCouponId(coupon.getId());
        redemption.setUserId(user.id());
        redemption.setCheckoutRef(request.checkoutRef());
        redemption.setDiscountAmount(result.discountAmount());
        redemption.setStatus(RedemptionStatus.RESERVED);
        redemptionRepository.save(redemption);

        coupon.setUsedCount(coupon.getUsedCount() + 1);
        couponRepository.save(coupon);
        log.info("Coupon {} redeemed by user {} for checkout {} (discount {})", coupon.getCode(), user.id(), request.checkoutRef(), result.discountAmount());
        return result;
    }

    /** Gives a use back. Safe to call any number of times, and for a checkout that never redeemed anything. */
    @Transactional
    public void release(String checkoutRef) {
        Optional<CouponRedemption> found = redemptionRepository.findByCheckoutRef(checkoutRef);
        if (found.isEmpty() || found.get().getStatus() == RedemptionStatus.RELEASED) {
            return;
        }
        CouponRedemption redemption = found.get();
        couponRepository.findByIdForUpdate(redemption.getCouponId()).ifPresent(coupon -> {
            coupon.setUsedCount(Math.max(0, coupon.getUsedCount() - 1));
            couponRepository.save(coupon);
        });
        redemption.setStatus(RedemptionStatus.RELEASED);
        redemption.setReleasedAt(Instant.now());
        redemptionRepository.save(redemption);
        log.info("Coupon use released for checkout {}", checkoutRef);
    }

    private CouponResult evaluate(Coupon coupon, Long userId, List<CouponLine> lines) {
        var eligible = CouponEngine.eligibleSubtotal(coupon, lines);
        long userUses = redemptionRepository.countByCouponIdAndUserIdAndStatus(coupon.getId(), userId, RedemptionStatus.RESERVED);

        Optional<String> rejection = CouponPolicy.reject(coupon, Instant.now(), eligible, userUses);
        if (rejection.isPresent()) {
            return CouponResult.invalid(coupon.getCode(), rejection.get());
        }
        CouponEngine.Result result = CouponEngine.calculate(coupon, lines);
        if (result.discount().signum() <= 0) {
            return CouponResult.invalid(coupon.getCode(), "This coupon does not give a discount on your cart.");
        }
        return CouponResult.ok(coupon.getCode(), coupon.getDescription(), result.discount(), result.allocations());
    }
}
