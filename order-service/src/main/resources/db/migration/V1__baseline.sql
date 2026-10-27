-- order-service baseline (Postgres). Version 1 = the schema BEFORE V2..V7 (those add outbox_events, the coupon /
-- checkout / payment columns, the order-line picture and variants).
-- An EXISTING database (created earlier by Hibernate) is adopted at version 1 by Flyway (baseline-on-migrate) and this
-- file is skipped; an EMPTY database (new machine) runs it, then V2..V7 bring it up to date.
-- Never edit a migration that has already been applied anywhere: add a new one.

CREATE TABLE orders (
    id                   BIGSERIAL      PRIMARY KEY,
    user_id              BIGINT         NOT NULL,
    seller_id            BIGINT,
    status               VARCHAR(255)   NOT NULL,
    total_amount         NUMERIC(12, 2) NOT NULL,
    cancellation_reason  VARCHAR(255),
    shipping_address_id  BIGINT,
    recipient_name       VARCHAR(255),
    shipping_address     VARCHAR(255),
    total_mrp_price      NUMERIC(12, 2),
    total_selling_price  NUMERIC(12, 2),
    discount             INTEGER,
    created_at           TIMESTAMPTZ    NOT NULL,
    delivery_date        TIMESTAMPTZ
);

CREATE INDEX idx_orders_user_id   ON orders (user_id);
CREATE INDEX idx_orders_seller_id ON orders (seller_id);

CREATE TABLE order_items (
    id             BIGSERIAL      PRIMARY KEY,
    order_id       BIGINT         NOT NULL REFERENCES orders (id),
    product_id     VARCHAR(255)   NOT NULL,
    product_name   VARCHAR(255),
    size           VARCHAR(255),
    quantity       INTEGER        NOT NULL,
    mrp_price      NUMERIC(12, 2),
    selling_price  NUMERIC(12, 2),
    user_id        BIGINT,
    seller_id      BIGINT,
    unit_price     NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
