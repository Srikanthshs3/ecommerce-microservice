package org.praskanth.controller;

import org.praskanth.config.UserApiCall;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.OrderRequest;
import org.praskanth.dto.OrderResponse;
import org.praskanth.dto.UserResponse;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final UserApiCall userApiCall;

    @PostMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> create(@Valid @RequestBody OrderRequest request,
                                                                   @AuthenticationPrincipal GatewayUser caller) {
        // Who is calling: the gateway-verified identity. Addresses are mutable data owned by user-service, so the
        // current profile is fetched (the identity headers are propagated automatically) rather than trusted from a token.
        UserResponse user = userApiCall.getUserProfile().getData();
        request.setUserId(caller.id());

        List<OrderResponse> response = orderService.create(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Order created", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(@PathVariable Long id,
                                                              @AuthenticationPrincipal GatewayUser caller) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getByIdForCaller(id, caller)));
    }

    /** A customer sees only their own orders (this used to trust the id in the URL: anyone could read anyone's orders). */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getByUser(@PathVariable Long userId,
                                                                      @AuthenticationPrincipal GatewayUser caller) {
        if (!caller.isAdmin() && !caller.id().equals(userId)) {
            throw new ForbiddenOperationException("You can only view your own orders.");
        }
        return ResponseEntity.ok(ApiResponse.success(orderService.getByUser(userId)));
    }

    /** Give up on one unpaid order. */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelUnpaid(@PathVariable Long id, @AuthenticationPrincipal GatewayUser caller) {
        orderService.cancelUnpaidByCustomer(id, caller);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled", null));
    }

    /** Give up on the whole unpaid checkout (all its orders). */
    @PostMapping("/checkout/{checkoutRef}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelCheckout(@PathVariable String checkoutRef,
                                                            @AuthenticationPrincipal GatewayUser caller) {
        orderService.cancelCheckoutByCustomer(checkoutRef, caller);
        return ResponseEntity.ok(ApiResponse.success("Checkout cancelled", null));
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getBySeller(@PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getBySeller(sellerId)));
    }
}
