package org.praskanth.controller;

import org.praskanth.config.UserApiCall;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.OrderRequest;
import org.praskanth.dto.OrderResponse;
import org.praskanth.dto.UserResponse;
import org.praskanth.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
                                                             @RequestHeader("Authorization") String jwt) {
        ApiResponse<UserResponse> apiResponse = userApiCall.getUserProfile(jwt);
        UserResponse user = apiResponse.getData();
        request.setUserId(user.getId());

        List<OrderResponse> response = orderService.create(request, user, jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Order created", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getById(id)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getByUser(userId)));
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getBySeller(@PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getBySeller(sellerId)));
    }
}
