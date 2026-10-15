package org.praskanth.controller;

import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.PaymentResponse;
import org.praskanth.exception.NotFoundException;
import org.praskanth.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByOrderId(@PathVariable Long orderId) {
        PaymentResponse response = paymentService.getByOrderId(orderId);
        if (response == null) {
            throw new NotFoundException("No payment recorded yet for order: " + orderId);
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
