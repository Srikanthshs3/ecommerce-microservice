package org.praskanth.entity;

/** What the customer chose at checkout. COD has no online gateway: the money is collected at the door. */
public enum PaymentMethod {
    RAZORPAY(PaymentGateway.RAZORPAY),
    STRIPE(PaymentGateway.STRIPE),
    COD(null);

    private final PaymentGateway gateway;

    PaymentMethod(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    /** The gateway that handles this method, or null for cash on delivery. */
    public PaymentGateway gateway() {
        return gateway;
    }

    public boolean isOnline() {
        return gateway != null;
    }
}
