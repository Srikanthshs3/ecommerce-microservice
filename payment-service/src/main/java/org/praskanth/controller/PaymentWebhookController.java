package org.praskanth.controller;

import lombok.RequiredArgsConstructor;
import org.praskanth.entity.PaymentGateway;
import org.praskanth.gateway.PaymentGatewayStrategy;
import org.praskanth.service.PaymentSettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Called by Razorpay / Stripe, not by browsers: no login, the SIGNATURE is the authentication (an unsigned or forged call
 * gets 400). The webhook is the safety net for a customer who paid and then closed the tab before the browser could confirm.
 * Raw body as a String: the signature is computed over the exact bytes.
 */
@RestController
@RequestMapping("/api/v1/payment/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final Map<PaymentGateway, PaymentGatewayStrategy> strategies;
    private final PaymentSettlementService settlement;

    @PostMapping("/razorpay")
    public ResponseEntity<String> razorpay(@RequestBody String payload,
                                           @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) throws Exception {
        settlement.onGatewayEvent(strategies.get(PaymentGateway.RAZORPAY).parseWebhook(payload, signature));
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> stripe(@RequestBody String payload,
                                         @RequestHeader(value = "Stripe-Signature", required = false) String signature) throws Exception {
        settlement.onGatewayEvent(strategies.get(PaymentGateway.STRIPE).parseWebhook(payload, signature));
        return ResponseEntity.ok("ok");
    }
}
