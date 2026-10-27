-- cart-service baseline (MySQL). Version 1 = the complete schema as it is today (see user-service V1 for the rules).

CREATE TABLE cart (
    id                   BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id              BIGINT,
    user_email           VARCHAR(255),
    total_selling_price  DECIMAL(12, 2),
    total_items          INT,
    total_mrp_price      DECIMAL(12, 2),
    discount             DECIMAL(12, 2),
    coupon_code          VARCHAR(255),
    coupon_discount      DECIMAL(12, 2),
    updated_at           DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_cart_user_id ON cart (user_id);

CREATE TABLE cart_item (
    id                  BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT,
    user_email          VARCHAR(255),
    cart_id             BIGINT,
    product_id          VARCHAR(255),
    product_name        VARCHAR(255),
    seller_id           BIGINT,
    image_url           VARCHAR(255),
    variant_id          VARCHAR(40),
    variant_label       VARCHAR(200),
    size                VARCHAR(255),
    quantity            INT,
    unit_mrp_price      DECIMAL(12, 2),
    unit_selling_price  DECIMAL(12, 2),
    total_mrp_price     DECIMAL(12, 2),
    total_selling_price DECIMAL(12, 2),
    CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id) REFERENCES cart (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_cart_item_cart ON cart_item (cart_id);

-- common-library scans an OutboxEvent entity into every JPA service. This service does not publish outbox events,
-- the table only exists so Hibernate schema validation (ddl-auto: validate) stays green.
CREATE TABLE outbox_events (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_type      VARCHAR(255)  NOT NULL,
    topic           VARCHAR(255)  NOT NULL,
    partition_key   VARCHAR(255)  NOT NULL,
    json_payload    TEXT          NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    attempt_count   INT           NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at    DATETIME(6)   NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_outbox_events_status_id ON outbox_events (status, id);
