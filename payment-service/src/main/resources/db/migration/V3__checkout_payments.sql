-- One payment per CHECKOUT (a checkout = the orders, one per seller, created by one "Place order" click).
-- The old per-order "payments" table is left untouched (history); new payments go to these two tables.

CREATE TABLE IF NOT EXISTS checkout_payments (
    id                 BIGSERIAL      PRIMARY KEY,
    checkout_ref       VARCHAR(64)    NOT NULL,
    user_id            BIGINT         NOT NULL,
    method             VARCHAR(20)    NOT NULL,              -- RAZORPAY / STRIPE / COD
    amount             NUMERIC(12, 2) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    status             VARCHAR(30)    NOT NULL,              -- CREATED / PENDING_COLLECTION / SUCCEEDED / PARTIALLY_REFUNDED / REFUNDED / CANCELLED
    gateway_order_id   VARCHAR(100),                         -- Razorpay order id or Stripe PaymentIntent id
    gateway_payment_id VARCHAR(100),                         -- Razorpay payment id or Stripe PaymentIntent id
    failure_reason     VARCHAR(255),
    attempts           INTEGER        NOT NULL DEFAULT 0,    -- failed tries reported by the browser
    refunded_amount    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ,
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_checkout_payments_ref UNIQUE (checkout_ref)
);

CREATE INDEX IF NOT EXISTS idx_checkout_payments_gateway_order ON checkout_payments (gateway_order_id);
CREATE INDEX IF NOT EXISTS idx_checkout_payments_user ON checkout_payments (user_id);

-- How the checkout amount is split over its orders (one row per order): used to publish one event per order
-- and to refund exactly one order's share when that order is cancelled.
CREATE TABLE IF NOT EXISTS payment_allocations (
    id              BIGSERIAL      PRIMARY KEY,
    payment_id      BIGINT         NOT NULL REFERENCES checkout_payments (id) ON DELETE CASCADE,
    order_id        BIGINT         NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    refunded_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    status          VARCHAR(20)    NOT NULL,                 -- ACTIVE / CANCELLED / REFUNDED
    refund_id       VARCHAR(100),
    CONSTRAINT uk_payment_allocations_order UNIQUE (order_id)
);

CREATE INDEX IF NOT EXISTS idx_payment_allocations_payment ON payment_allocations (payment_id);
