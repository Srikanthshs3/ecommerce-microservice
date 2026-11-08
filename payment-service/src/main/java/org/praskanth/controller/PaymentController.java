package org.praskanth.controller;

import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.PaymentDtos.*;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** Which payment methods this installation offers (a gateway without test keys is reported as disabled). */
    @GetMapping("/methods")
    public ResponseEntity<ApiResponse<List<MethodInfo>>> methods() {
        return ResponseEntity.ok(ApiResponse.success(paymentService.methods()));
    }

    /** State of one checkout: stock reservation, payment window, payment status. The payment page polls this. */
    @GetMapping("/checkout/{checkoutRef}")
    public ResponseEntity<ApiResponse<CheckoutSummary>> summary(@PathVariable String checkoutRef,
                                                                @AuthenticationPrincipal GatewayUser caller) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.summary(checkoutRef, caller)));
    }

    /** Pick a method. Online: returns what the browser needs to open the payment form. COD: the order is placed right away. */
    @PostMapping("/checkout/{checkoutRef}/initiate")
    public ResponseEntity<ApiResponse<InitiateResponse>> initiate(@PathVariable String checkoutRef,
                                                                  @RequestBody InitiateRequest request,
                                                                  @AuthenticationPrincipal GatewayUser caller) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.initiate(checkoutRef, request.method(), caller)));
    }

    /** After the customer paid in the browser: we verify with the gateway, then the orders are placed. */
    @PostMapping("/checkout/{checkoutRef}/confirm")
    public ResponseEntity<ApiResponse<CheckoutSummary>> confirm(@PathVariable String checkoutRef,
                                                                @RequestBody ConfirmRequest request,
                                                                @AuthenticationPrincipal GatewayUser caller) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.confirm(checkoutRef, request, caller)));
    }

    @PostMapping("/checkout/{checkoutRef}/failed")
    public ResponseEntity<ApiResponse<Void>> failed(@PathVariable String checkoutRef,
                                                    @RequestBody FailureRequest request,
                                                    @AuthenticationPrincipal GatewayUser caller) {
        paymentService.reportFailure(checkoutRef, request.reason(), caller);
        return ResponseEntity.ok(ApiResponse.success("Recorded", null));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<OrderPaymentView>> byOrder(@PathVariable Long orderId,
                                                                 @AuthenticationPrincipal GatewayUser caller) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.byOrder(orderId, caller)));
    }
}
