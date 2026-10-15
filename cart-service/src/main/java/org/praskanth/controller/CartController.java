package org.praskanth.controller;

import org.praskanth.dto.*;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    // The caller's identity comes from the gateway-verified X-User-* headers (no JWT, no call to user-service).

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> findUserCartHandler(@AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success("User cart found", cartService.getUserCart(user)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CartItemResponse>> addItem(@Valid @RequestBody AddItemRequest request,
                                                                 @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success("Item added to cart", cartService.addItem(user, request)));
    }

    /** Applies a coupon code to the cart. A code that cannot be used is answered with 400 and the reason. */
    @PostMapping("/coupon")
    public ResponseEntity<ApiResponse<CartResponse>> applyCoupon(@Valid @RequestBody ApplyCouponRequest request,
                                                                 @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success("Coupon applied", cartService.applyCoupon(user, request.getCode())));
    }

    @DeleteMapping("/coupon")
    public ResponseEntity<ApiResponse<CartResponse>> removeCoupon(@AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success("Coupon removed", cartService.removeCoupon(user)));
    }

    /** Removes every item from the caller's cart (used after a successful checkout). */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(@AuthenticationPrincipal GatewayUser user) {
        cartService.clearCart(user);
        return ResponseEntity.ok(ApiResponse.success("Cart cleared", null));
    }

    @DeleteMapping("/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> deleteCartItem(@PathVariable Long cartItemId,
                                                            @AuthenticationPrincipal GatewayUser user) {
        cartService.deleteCartItem(user.id(), cartItemId);
        return ResponseEntity.ok(ApiResponse.success("Item deleted from cart", null));
    }

    @PutMapping("/{cartItemId}")
    public ResponseEntity<ApiResponse<CartItemResponse>> updateCartItem(@PathVariable Long cartItemId,
                                                                        @Valid @RequestBody UpdateQuantityRequest request,
                                                                        @AuthenticationPrincipal GatewayUser user) {
        CartItemResponse updatedItem = cartService.updateCart(user.id(), cartItemId, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success("Item quantity updated", updatedItem));
    }
}