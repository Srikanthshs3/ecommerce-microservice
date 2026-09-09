package org.praskanth.service;

import org.praskanth.config.InventoryApiClient;
import org.praskanth.config.ProductApiClient;
import org.praskanth.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.exception.BadRequestException;
import org.praskanth.modal.Cart;
import org.praskanth.modal.CartItem;
import org.praskanth.repository.CartItemRepository;
import org.praskanth.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final InventoryApiClient inventoryApiClient;
    private final ProductApiClient productApiClient;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    @Transactional
    public CartItemResponse addItem(UserResponse user, AddItemRequest request) {
        if (!inventoryApiClient.checkAvailability(request.getProductId(), request.getQuantity())) {
            throw new BadRequestException("Requested quantity is out of stock for product: " + request.getProductId());
        }

        ProductResponse product = productApiClient.getProductById(request.getProductId());


        Cart cart = getOrCreateCart(user); // Now passes the whole user object

        CartItem item = cartItemRepository.findByCartAndProductIdAndSize(cart, request.getProductId(), request.getSize());
        BigDecimal qty = BigDecimal.valueOf(request.getQuantity());

        if (item != null) {
            // Increment existing item safely using the baseline UNIT price
            int newQty = item.getQuantity() + request.getQuantity();
            item.setQuantity(newQty);
            item.setTotalMrpPrice(item.getUnitMrpPrice().multiply(BigDecimal.valueOf(newQty)));
            item.setTotalSellingPrice(item.getUnitSellingPrice().multiply(BigDecimal.valueOf(newQty)));
        } else {
            // Create new item
            item = new CartItem();
            item.setCart(cart);
            item.setProductId(request.getProductId());
            item.setProductName(product.getName());
            item.setSellerId(product.getSellerId());
            item.setSize(request.getSize());
            item.setQuantity(request.getQuantity());
            item.setUserId(user.getId());
            item.setUserEmail(user.getEmail());

            // Set baseline unit prices
            item.setUnitMrpPrice(product.getMrpPrice());
            item.setUnitSellingPrice(product.getSellingPrice());

            // Set totals based on quantity
            item.setTotalMrpPrice(product.getMrpPrice().multiply(qty));
            item.setTotalSellingPrice(product.getSellingPrice().multiply(qty));

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

    // --- Helper Methods ---

    // Uses .orElseGet() for a much cleaner implementation
    public Cart getOrCreateCart(UserResponse user) {
        return cartRepository.findByUserId(user.getId()).orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.setUserId(user.getId());
            newCart.setUserEmail(user.getEmail());
            newCart.setTotalMrpPrice(BigDecimal.ZERO);
            newCart.setTotalSellingPrice(BigDecimal.ZERO);
            newCart.setTotalItems(0);
            return cartRepository.save(newCart);
        });
    }

    public CartResponse getUserCart(UserResponse user) {
        Cart cart = getOrCreateCart(user);
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
                .orElseThrow(() -> new BadRequestException("Cart item not found with id: " + id));
    }

    public CartResponse toCartResponse(Cart cart) {
        if (cart == null) return null;

        List<CartItemResponse> itemResponses = cart.getCartItems() == null ? Collections.emptyList() :
                cart.getCartItems().stream()
                        .map(this::toCartItemResponse)
                        .collect(Collectors.toList());

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
                .build();
    }

    public CartItemResponse toCartItemResponse(CartItem item) {
        if (item == null) return null;

        return CartItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .sellerId(item.getSellerId())
                .imageUrl(item.getImageUrl())
                .size(item.getSize())
                .quantity(item.getQuantity())
                .unitMrpPrice(item.getUnitMrpPrice())
                .unitSellingPrice(item.getUnitSellingPrice())
                .totalMrpPrice(item.getTotalMrpPrice())
                .totalSellingPrice(item.getTotalSellingPrice())
                .build();
    }
}