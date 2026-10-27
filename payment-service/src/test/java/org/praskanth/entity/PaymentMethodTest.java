package org.praskanth.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMethodTest {

    @Test
    void razorpay_isOnlineAndMapsToRazorpayGateway() {
        assertThat(PaymentMethod.RAZORPAY.isOnline()).isTrue();
        assertThat(PaymentMethod.RAZORPAY.gateway()).isEqualTo(PaymentGateway.RAZORPAY);
    }

    @Test
    void stripe_isOnlineAndMapsToStripeGateway() {
        assertThat(PaymentMethod.STRIPE.isOnline()).isTrue();
        assertThat(PaymentMethod.STRIPE.gateway()).isEqualTo(PaymentGateway.STRIPE);
    }

    @Test
    void cod_isNotOnlineAndHasNoGateway() {
        assertThat(PaymentMethod.COD.isOnline()).isFalse();
        assertThat(PaymentMethod.COD.gateway()).isNull();
    }
}
