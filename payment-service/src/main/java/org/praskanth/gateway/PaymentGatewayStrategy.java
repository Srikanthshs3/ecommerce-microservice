package org.praskanth.gateway;

import com.razorpay.RazorpayException;
import org.praskanth.entity.PaymentGateway;

import java.math.BigDecimal;

public interface PaymentGatewayStrategy {
    PaymentGateway  getGateway();

    String executePayment(Long orderId, BigDecimal amount, String currency) throws Exception;
}
