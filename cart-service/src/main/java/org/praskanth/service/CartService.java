package org.praskanth.service;

import org.praskanth.config.CouponApiClient;
import org.praskanth.config.InventoryApiClient;
import org.praskanth.config.ProductApiClient;
import org.praskanth.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.exception.BadRequestException;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.modal.Cart;
import org.praskanth.modal.CartItem;
import org.praskanth.repository.CartItemRepository;
import org.praskanth.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final InventoryApiClient inventoryApiClient;
    private final ProductApiClient productApiClient;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CouponApiClient couponApiClient;

    @Transactional
    public CartItemResponse addItem(GatewayUser user, AddItemRequest request) {
        ProductResponse product = productApiClient.getProductById(request.getProductId());

        // A product WITH variants is bought as one specific variant (colour/size ...): its own price, picture and stock.
        ProductResponse.Variant variant = null;
        boolean hasVariants = product.getVariants() != null && !product.getVariants().isEmpty();
        if (hasVariants) {
            if (request.getVariantId() == null || request.getVariantId().isBlank()) {
                throw new BadRequestException("Please choose "
                        + (product.getOptionNames() == null ? "an option" : String.join(" and ", product.getOptionNames())) + " first.");
            }
            variant = product.getVariants().stream()
                    .filter(v -> request.getVariantId().equals(v.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("That option is no longer available. Please reload the product page."));
        } else if (request.getVariantId() != null && !request.getVariantId().isBlank()) {
            throw new BadRequestException("This product has no variants.");
        }

        // stock of a variant is kept under productId:variantId
        String stockId = variant == null ? request.getProductId() : request.getProductId() + ":" + variant.getId();
        if (!inventoryApiClient.checkAvailability(stockId, request.getQuantity())) {
            throw new BadRequestException("Requested quantity is out of stock for product: " + product.getName()
                    + (variant == null ? "" : " (" + variant.getLabel() + ")"));
        }

        Cart cart = getOrCreateCart(user); // Now passes the whole user object

        CartItem item = variant == null
                ? cartItemRepository.findByCartAndProductIdAndSize(cart, request.getProductId(), request.getSize())
                : cartItemRepository.findByCartAndProductIdAndVariantId(cart, request.getProductId(), variant.getId());
        BigDecimal qty = BigDecimal.valueOf(request.getQuantity());
        BigDecimal unitMrp = variant == null ? product.getMrpPrice() : variant.getMrpPrice();
        BigDecimal unitSelling = variant == null ? product.getSellingPrice() : variant.getSellingPrice();
        String picture = variant == null ? mainImage(product)
                : (variant.getImageUrl() == null || variant.getImageUrl().isEmpty() ? mainImage(product) : variant.getImageUrl().get(0));

        if (item != null) {
            // Increment existing item safely using the baseline UNIT price
            int newQty = item.getQuantity() + request.getQuantity();
            item.setQuantity(newQty);
            if (item.getImageUrl() == null) {
                item.setImageUrl(picture); // lines added before pictures existed get one the next time
            }
            item.setTotalMrpPrice(item.getUnitMrpPrice().multiply(BigDecimal.valueOf(newQty)));
            item.setTotalSellingPrice(item.getUnitSellingPrice().multiply(BigDecimal.valueOf(newQty)));
        } else {
            // Create new item
            item = new CartItem();
            item.setCart(cart);
            item.setProductId(request.getProductId());
            item.setProductName(product.getName());
            item.setImageUrl(picture);
            item.setSellerId(product.getSellerId());
            item.setVariantId(variant == null ? null : variant.getId());
            item.setVariantLabel(variant == null ? null : variant.getLabel());
            item.setSize(variant == null ? request.getSize() : null);
            item.setQuantity(request.getQuantity());
            item.setUserId(user.id());
            item.setUserEmail(user.email());

            // Set baseline unit prices
            item.setUnitMrpPrice(unitMrp);
            item.setUnitSellingPrice(unitSelling);

            // Set totals based on quantity
            item.setTotalMrpPrice(unitMrp.multiply(qty));
            item.setTotalSellingPrice(unitSelling.multiply(qty));

            cart.getCartItems().add(item);
        }

        CartItem savedItem = cartItemRepository.save(item);
        recalculateCartTotals(cart);
        return toCartItemResponse(savedItem);
    }

    @Transactional
    public CartItemResponse updateCart(Long userId, Long cartItemId, Integer newQuantity) {
        CartItem item = findCartItemById(cartItemId);

        if (!item.getUserId().equals(userId)) {
            throw new BadRequestException("You are not allowed to update this cart item");
        }

        // Multiply by the baseline UNIT price, ensuring accurate math no matter how many times it is updated
        item.setQuantity(newQuantity);
        item.setTotalMrpPrice(item.getUnitMrpPrice().multiply(BigDecimal.valueOf(newQuantity)));
        item.setTotalSellingPrice(item.getUnitSellingPrice().multiply(BigDecimal.valueOf(newQuantity)));

        CartItem updatedItem = cartItemRepository.save(item);
        recalculateCartTotals(item.getCart());
        return toCartItemResponse(updatedItem);
    }

    @Transactional
    public void deleteCartItem(Long userId, Long cartItemId) {
        CartItem item = findCartItemById(cartItemId);

        if (!item.getUserId().equals(userId)) {
            throw new BadRequestException("You are not allowed to delete this cart item");
        }

        Cart cart = item.getCart();
        cart.getCartItems().remove(item);
        cartItemRepository.delete(item);

        recalculateCartTotals(cart);
    }

    /** Empties the caller's cart. Idempotent: no cart or an already-empty cart is a no-op. Called by order-service after an order is placed. */
    @Transactional
    public void clearCart(GatewayUser user) {
        cartRepository.findByUserId(user.id()).ifPresent(cart -> {
            cart.getCartItems().clear(); // orphanRemoval deletes the rows
            cart.setCouponCode(null);
            cart.setCouponDiscount(BigDecimal.ZERO);
            recalculateCartTotals(cart);
        });
    }

    // ------------------------------------------------------------------ coupons

    /** Applies a coupon to the caller's cart; rejects it with the coupon-service's customer-friendly reason. */
    @Transactional
    public CartResponse applyCoupon(GatewayUser user, String code) {
        Cart cart = getOrCreateCart(user);
        if (cart.getCartItems().isEmpty()) {
            throw new BadRequestException("Your cart is empty. Add items before applying a coupon.");
        }
        CouponResult result = couponApiClient.validate(code, couponLines(cart));
        if (!result.isValid()) {
            throw new BadRequestException(result.getMessage());
        }
        cart.setCouponCode(result.getCode());
        cart.setCouponDiscount(result.getDiscountAmount());
        cartRepository.save(cart);
        return toCartResponse(cart);
    }

    @Transactional
    public CartResponse removeCoupon(GatewayUser user) {
        Cart cart = getOrCreateCart(user);
        dropCoupon(cart);
        cartRepository.save(cart);
        return toCartResponse(cart);
    }

    /**
     * Re-checks the cart's coupon against the current items (they may have changed, or the coupon may have expired).
     * Not valid any more -> the coupon is removed. coupon-service unreachable -> keep it, but never discount more than
     * the cart is worth; order-service validates strictly again at checkout, which is the authoritative check.
     */
    private void refreshCoupon(Cart cart) {
        if (cart.getCouponCode() == null) {
            cart.setCouponDiscount(BigDecimal.ZERO);
            return;
        }
        if (cart.getCartItems().isEmpty()) {
            dropCoupon(cart);
            return;
        }
        try {
            CouponResult result = couponApiClient.validate(cart.getCouponCode(), couponLines(cart));
            if (result.isValid()) {
                cart.setCouponDiscount(result.getDiscountAmount());
            } else {
                log.info("Coupon {} removed from cart {}: {}", cart.getCouponCode(), cart.getId(), result.getMessage());
                dropCoupon(cart);
            }
        } catch (Exception e) {
            log.warn("Could not re-check coupon {} ({}); keeping the last known discount", cart.getCouponCode(), e.getMessage());
            BigDecimal previous = cart.getCouponDiscount() == null ? BigDecimal.ZERO : cart.getCouponDiscount();
            cart.setCouponDiscount(previous.min(cart.getTotalSellingPrice()));
        }
    }

    private void dropCoupon(Cart cart) {
        cart.setCouponCode(null);
        cart.setCouponDiscount(BigDecimal.ZERO);
    }

    /** What the customer would pay per seller: the input coupon-service needs to discount seller-specific coupons correctly. */
    private List<CouponLine> couponLines(Cart cart) {
        Map<Long, BigDecimal> bySeller = new LinkedHashMap<>();
        for (CartItem item : cart.getCartItems()) {
            bySeller.merge(item.getSellerId(), item.getTotalSellingPrice(), BigDecimal::add);
        }
        return bySeller.entrySet().stream().map(e -> new CouponLine(e.getKey(), e.getValue())).toList();
    }

    // --- Helper Methods ---

    // Uses .orElseGet() for a much cleaner implementation
    public Cart getOrCreateCart(GatewayUser user) {
        return cartRepository.findByUserId(user.id()).orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.setUserId(user.id());
            newCart.setUserEmail(user.email());
            newCart.setTotalMrpPrice(BigDecimal.ZERO);
            newCart.setTotalSellingPrice(BigDecimal.ZERO);
            newCart.setTotalItems(0);
            return cartRepository.save(newCart);
        });
    }

    public CartResponse getUserCart(GatewayUser user) {
        Cart cart = getOrCreateCart(user);
        if (cart.getCouponCode() != null) { // keep the shown discount honest (items changed / coupon expired)
            refreshCoupon(cart);
            cartRepository.save(cart);
        }
        return toCartResponse(cart);
    }

    private void recalculateCartTotals(Cart cart) {
        BigDecimal totalMrp = BigDecimal.ZERO;
        BigDecimal totalSelling = BigDecimal.ZERO;
        int totalItems = 0;

        for (CartItem item : cart.getCartItems()) {
            totalMrp = totalMrp.add(item.getTotalMrpPrice());
            totalSelling = totalSelling.add(item.getTotalSellingPrice());
            totalItems += item.getQuantity();
        }

        cart.setTotalMrpPrice(totalMrp);
        cart.setTotalSellingPrice(totalSelling);
        cart.setTotalItems(totalItems);
        cart.setDiscount(calculateDiscountPercentage(totalMrp, totalSelling));
        refreshCoupon(cart);

        cartRepository.save(cart);
    }

    private BigDecimal calculateDiscountPercentage(BigDecimal mrpPrice, BigDecimal sellingPrice) {
        if (mrpPrice == null || mrpPrice.compareTo(sellingPrice) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount = mrpPrice.subtract(sellingPrice);
        return discount.divide(mrpPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    public CartItem findCartItemById(Long id) {
        return cartItemRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("That item is no longer in your cart."));
    }

    public CartResponse toCartResponse(Cart cart) {
        if (cart == null) return null;

        List<CartItemResponse> itemResponses = cart.getCartItems() == null ? Collections.emptyList() :
                cart.getCartItems().stream()
                        .map(this::toCartItemResponse)
                        .collect(Collectors.toList());

        BigDecimal couponDiscount = cart.getCouponDiscount() == null ? BigDecimal.ZERO : cart.getCouponDiscount();
        BigDecimal selling = cart.getTotalSellingPrice() == null ? BigDecimal.ZERO : cart.getTotalSellingPrice();
        BigDecimal payable = selling.subtract(couponDiscount).max(BigDecimal.ZERO);

        return CartResponse.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .userEmail(cart.getUserEmail())
                .cartItems(itemResponses)
                .totalSellingPrice(cart.getTotalSellingPrice())
                .totalMrpPrice(cart.getTotalMrpPrice())
                .totalItems(cart.getTotalItems())
                .discount(cart.getDiscount())
                .couponCode(cart.getCouponCode())
                .couponDiscount(couponDiscount)
                .payableAmount(payable)
                .build();
    }

    /** First picture of the product, or null when it has none. */
    private static String mainImage(ProductResponse product) {
        return product.getImageUrl() == null || product.getImageUrl().isEmpty() ? null : product.getImageUrl().get(0);
    }

    public CartItemResponse toCartItemResponse(CartItem item) {
        if (item == null) return null;

        return CartItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .sellerId(item.getSellerId())
                .imageUrl(item.getImageUrl())
                .variantId(item.getVariantId())
                .variantLabel(item.getVariantLabel())
                .size(item.getSize())
                .quantity(item.getQuantity())
                .unitMrpPrice(item.getUnitMrpPrice())
                .unitSellingPrice(item.getUnitSellingPrice())
                .totalMrpPrice(item.getTotalMrpPrice())
                .totalSellingPrice(item.getTotalSellingPrice())
                .build();
    }
}