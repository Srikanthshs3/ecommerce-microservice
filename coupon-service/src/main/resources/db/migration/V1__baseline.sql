-- coupon-service baseline (MySQL). Version 1 = the complete schema as it is today (see user-service V1 for the rules).

CREATE TABLE coupons (
    id                   BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code                 VARCHAR(30)    NOT NULL,
    description          VARCHAR(255),
    discount_type        VARCHAR(20)    NOT NULL,
    discount_value       DECIMAL(12, 2) NOT NULL,
    max_discount_amount  DECIMAL(12, 2),
    min_order_amount     DECIMAL(12, 2),
    valid_from           DATETIME(6)    NOT NULL,
    valid_until          DATETIME(6),
    usage_limit          INT,
    per_user_limit       INT,
    used_count           INT            NOT NULL DEFAULT 0,
    active               BIT(1)         NOT NULL DEFAULT 1,
    listed               BIT(1)         NOT NULL DEFAULT 0,
    seller_id            BIGINT,
    created_by           BIGINT         NOT NULL,
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,
    CONSTRAINT uk_coupon_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_coupon_seller ON coupons (seller_id);

CREATE TABLE coupon_redemptions (
    id               BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    coupon_id        BIGINT         NOT NULL,
    user_id          BIGINT         NOT NULL,
    checkout_ref     VARCHAR(64)    NOT NULL,
    discount_amount  DECIMAL(12, 2) NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    created_at       DATETIME(6)    NOT NULL,
    released_at      DATETIME(6),
    CONSTRAINT uk_redemption_checkout UNIQUE (checkout_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_redemption_coupon_user ON coupon_redemptions (coupon_id, user_id, status);

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
