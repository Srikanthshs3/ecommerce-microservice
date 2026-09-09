package org.praskanth.controller;

import org.praskanth.config.UserApiCall;
import org.praskanth.dto.*;
import org.praskanth.modal.Cart;
import org.praskanth.modal.CartItem;
import org.praskanth.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final UserApiCall userApiCall;

    private UserResponse getAuthenticatedUser(String jwt) {
        return userApiCall.getUserProfile(jwt).getData();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> findUserCartHandler(@RequestHeader("Authorization") String jwt) {
        UserResponse user = getAuthenticatedUser(jwt);
        return ResponseEntity.ok(ApiResponse.success("User cart found", cartService.getUserCart(user)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CartItemResponse>> addItem(@Valid @RequestBody AddItemRequest request,
                                                         @RequestHeader("Authorization") String jwt) {
        UserResponse user = getAuthenticatedUser(jwt);
        return ResponseEntity.ok(ApiResponse.success("Item added to cart", cartService.addItem(user, request)));
    }

    @DeleteMapping("/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> deleteCartItem(@PathVariable Long cartItemId,
                                                            @RequestHeader("Authorization") String jwt) {
        UserResponse user = getAuthenticatedUser(jwt);
        cartService.deleteCartItem(user.getId(), cartItemId);
        return ResponseEntity.ok(ApiResponse.success("Item deleted from cart", null));
    }

    @PutMapping("/{cartItemId}")
    public ResponseEntity<ApiResponse<CartItemResponse>> updateCartItem(@PathVariable Long cartItemId,
                                                                @Valid @RequestBody UpdateQuantityRequest request,
                                                                @RequestHeader("Authorization") String jwt) {
        UserResponse user = getAuthenticatedUser(jwt);
        CartItemResponse updatedItem = cartService.updateCart(user.getId(), cartItemId, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success("Item quantity updated", updatedItem));
    }
}