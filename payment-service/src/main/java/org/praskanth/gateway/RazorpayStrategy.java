package org.praskanth.gateway;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.praskanth.entity.PaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RazorpayStrategy implements PaymentGatewayStrategy{

    @Value("${razorpay.key-id:rzp_test_dummy}")
    private String keyId;

    @Value("${razorpay.key-secret:dummy_secret}")
    private String keySecret;

    @Override
    public PaymentGateway getGateway() {
        return PaymentGateway.RAZORPAY;
    }

    @Override
    public String executePayment(Long orderId, BigDecimal amount, String currency) throws Exception {
        RazorpayClient client = new RazorpayClient(keyId, keySecret);

        JSONObject orderRequest = new JSONObject();
        // Convert to paise
        orderRequest.put("amount", amount.multiply(BigDecimal.valueOf(100)).intValue());
        orderRequest.put("currency", currency != null ? currency : "INR");
        orderRequest.put("receipt", "rcpt_order_" + orderId);

        Order razorpayOrder = client.orders.create(orderRequest);
        String rzpOrderId = razorpayOrder.get("id");
        log.info("Created Razorpay order {} for platform order {}", rzpOrderId, orderId);

        // For automated backend processing, return transaction reference
        return "rzp_txn_" + rzpOrderId.replace("order_", "") + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

}
