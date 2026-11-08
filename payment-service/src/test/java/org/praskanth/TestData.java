package org.praskanth;

import org.praskanth.dto.CheckoutOrder;
import org.praskanth.entity.AllocationStatus;
import org.praskanth.entity.CheckoutPayment;
import org.praskanth.entity.CheckoutPaymentStatus;
import org.praskanth.entity.PaymentAllocation;
import org.praskanth.entity.PaymentMethod;
import org.praskanth.security.gateway.GatewayUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/** Small shared builders for the payment-service tests. */
public final class TestData {

    private TestData() {
    }

    public static GatewayUser customer(long id) {
        return new GatewayUser(id, "user" + id + "@example.com", Set.of("CUSTOMER"));
    }

    public static GatewayUser admin(long id) {
        return new GatewayUser(id, "admin" + id + "@example.com", Set.of("ADMIN"));
    }

    public static CheckoutOrder order(long id, long userId, String status, String total) {
        CheckoutOrder order = new CheckoutOrder();
        order.setId(id);
        order.setUserId(userId);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal(total));
        order.setCheckoutRef("co-1");
        return order;
    }

    public static CheckoutPayment payment(long id, String ref, long userId, PaymentMethod method,
                                          CheckoutPaymentStatus status, String amount) {
        return CheckoutPayment.builder()
                .id(id).checkoutRef(ref).userId(userId).method(method)
                .amount(new BigDecimal(amount)).currency("INR").status(status).build();
    }

    /** Adds an allocation (ACTIVE by default) to the payment and returns it. */
    public static PaymentAllocation allocate(CheckoutPayment payment, long orderId, String amount) {
        PaymentAllocation allocation = PaymentAllocation.builder()
                .payment(payment).orderId(orderId).amount(new BigDecimal(amount))
                .status(AllocationStatus.ACTIVE).build();
        payment.getAllocations().add(allocation);
        return allocation;
    }
}
