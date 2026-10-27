-- payment-service baseline (Postgres). Version 1 = the schema BEFORE V2 (outbox_events) and V3 (checkout_payments,
-- payment_allocations). "payments" is the original per-order table; it is kept for history.
-- Adopted at version 1 on an existing database, executed on an empty one. See order-service V1 for the rules.

CREATE TABLE payments (
    id                BIGSERIAL      PRIMARY KEY,
    order_id          BIGINT         NOT NULL,
    amount            NUMERIC(12, 2) NOT NULL,
    status            VARCHAR(255)   NOT NULL,
    gateway           VARCHAR(255)   NOT NULL,
    gateway_order_id  VARCHAR(255),
    transaction_id    VARCHAR(255),
    failure_reason    VARCHAR(255),
    created_at        TIMESTAMPTZ    NOT NULL,
    completed_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_payments_order_id      ON payments (order_id);
CREATE INDEX        idx_payments_gateway_order ON payments (gateway_order_id);
