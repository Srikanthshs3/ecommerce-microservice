-- inventory-service baseline (MySQL). Version 1 = the schema BEFORE V2 (outbox_events is created by the existing V2).
-- See user-service V1 for the rules.

CREATE TABLE inventory (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id          VARCHAR(255)  NOT NULL,          -- product id, or "<productId>:<variantId>" for a variant
    product_name        VARCHAR(255)  NOT NULL,
    seller_id           BIGINT        NOT NULL,
    available_quantity  INT           NOT NULL DEFAULT 0,
    reserved_quantity   INT           NOT NULL DEFAULT 0,
    version             BIGINT,                          -- optimistic locking (@Version)
    updated_at          DATETIME(6)   NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE UNIQUE INDEX idx_inventory_product_id ON inventory (product_id);
CREATE INDEX idx_inventory_seller_id ON inventory (seller_id);

CREATE TABLE order_reservations (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT        NOT NULL,
    product_id  VARCHAR(255)  NOT NULL,
    quantity    INT           NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    CONSTRAINT uk_order_reservations_order_product UNIQUE (order_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
