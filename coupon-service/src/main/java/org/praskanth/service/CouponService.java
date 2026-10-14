package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.AvailableCouponResponse;
import org.praskanth.dto.CouponRequest;
import org.praskanth.dto.CouponResponse;
import org.praskanth.entity.Coupon;
import org.praskanth.entity.DiscountType;
import org.praskanth.entity.RedemptionStatus;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.repository.CouponRedemptionRepository;
import org.praskanth.repository.CouponRepository;
import org.praskanth.security.gateway.GatewayUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Coupon management. ADMIN manages every coupon (platform-wide ones, or ones tied to a seller);
 * a SELLER manages only coupons of their own products.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;

    @Transactional
    public CouponResponse create(GatewayUser user, CouponRequest request) {
        String code = RedemptionService.normalize(request.getCode());
        if (couponRepository.existsByCode(code)) {
            throw new ConflictException("A coupon with code " + code + " already exists.");
        }
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setCreatedBy(user.id());
        coupon.setSellerId(user.isAdmin() ? request.getSellerId() : user.id());
        coupon.setUsedCount(0);
        apply(coupon, request);
        Coupon saved = couponRepository.saveAndFlush(coupon);
        log.info("Coupon {} created by user {}", saved.getCode(), user.id());
        return CouponResponse.from(saved, Instant.now());
    }

    @Transactional
    public CouponResponse update(GatewayUser user, Long id, CouponRequest request) {
        Coupon coupon = findManageable(user, id);
        apply(coupon, request); // the code and the owning seller never change
        if (coupon.getUsageLimit() != null && coupon.getUsageLimit() < coupon.getUsedCount()) {
            throw new BadRequestException("Usage limit cannot be lower than the " + coupon.getUsedCount() + " uses already made.");
        }
        return CouponResponse.from(couponRepository.saveAndFlush(coupon), Instant.now());
    }

    /** A coupon that was ever used must stay (order history refers to it): deactivate it instead. */
    @Transactional
    public void delete(GatewayUser user, Long id) {
        Coupon coupon = findManageable(user, id);
        if (redemptionRepository.existsByCouponId(coupon.getId())) {
            throw new ConflictException("This coupon has been used, so it cannot be deleted. Deactivate it instead.");
        }
        couponRepository.delete(coupon);
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> listManageable(GatewayUser user) {
        Instant now = Instant.now();
        List<Coupon> coupons = user.isAdmin()
                ? couponRepository.findAllByOrderByCreatedAtDesc()
                : couponRepository.findBySellerIdOrderByCreatedAtDesc(user.id());
        return coupons.stream().map(c -> CouponResponse.from(c, now)).toList();
    }

    /** Listed coupons this customer could still use (inside dates, not exhausted, their own limit not reached). */
    @Transactional(readOnly = true)
    public List<AvailableCouponResponse> available(GatewayUser user) {
        return couponRepository.findAvailable(Instant.now()).stream()
                .filter(c -> c.getPerUserLimit() == null
                        || redemptionRepository.countByCouponIdAndUserIdAndStatus(c.getId(), user.id(), RedemptionStatus.RESERVED) < c.getPerUserLimit())
                .map(AvailableCouponResponse::from)
                .toList();
    }

    // ------------------------------------------------------------------------------------------------

    private Coupon findManageable(GatewayUser user, Long id) {
        Coupon coupon = couponRepository.findById(id).orElseThrow(() -> new NotFoundException("Coupon not found: " + id));
        if (!user.isAdmin() && !user.id().equals(coupon.getSellerId())) {
            throw new ForbiddenOperationException("You can only manage your own coupons.");
        }
        return coupon;
    }

    /** Copies the editable fields and checks the rules that span several fields. */
    private void apply(Coupon coupon, CouponRequest r) {
        if (r.getDiscountType() == DiscountType.PERCENTAGE && r.getDiscountValue().compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("A percentage discount cannot be more than 100.");
        }
        if (r.getDiscountType() == DiscountType.FIXED && r.getMaxDiscountAmount() != null) {
            throw new BadRequestException("maxDiscountAmount only applies to percentage coupons.");
        }
        Instant from = r.getValidFrom() != null ? r.getValidFrom() : (coupon.getValidFrom() != null ? coupon.getValidFrom() : Instant.now());
        if (r.getValidUntil() != null && !r.getValidUntil().isAfter(from)) {
            throw new BadRequestException("validUntil must be after validFrom.");
        }

        coupon.setDescription(r.getDescription() == null || r.getDescription().isBlank() ? null : r.getDescription().trim());
        coupon.setDiscountType(r.getDiscountType());
        coupon.setDiscountValue(r.getDiscountValue());
        coupon.setMaxDiscountAmount(r.getMaxDiscountAmount());
        coupon.setMinOrderAmount(r.getMinOrderAmount());
        coupon.setValidFrom(from);
        coupon.setValidUntil(r.getValidUntil());
        coupon.setUsageLimit(r.getUsageLimit());
        coupon.setPerUserLimit(r.getPerUserLimit());
        coupon.setActive(r.getActive() == null || r.getActive());
        coupon.setListed(r.getListed() != null && r.getListed());
    }
}
