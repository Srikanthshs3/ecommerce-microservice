package org.praskanth.gateway;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.entity.PaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
public class StripeStrategy implements PaymentGatewayStrategy{

    @Value("${stripe.secret-key:sk_test_dummy}")
    private String secretKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }

    @Override
    public PaymentGateway getGateway() {
        return PaymentGateway.STRIPE;
    }

    @Override
    public String executePayment(Long orderId, BigDecimal amount, String currency) throws Exception {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
                .setCurrency(currency != null ? currency.toLowerCase() : "inr")
                .putMetadata("orderId", orderId.toString())
                .setPaymentMethod("pm_card_visa") // default test token for immediate backend confirmation
                .setConfirm(true)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                .build()
                )
                .build();

        PaymentIntent intent = PaymentIntent.create(params);
        log.info("Created and confirmed Stripe PaymentIntent {} for order {}", intent.getId(), orderId);
        return intent.getId();
    }


}
