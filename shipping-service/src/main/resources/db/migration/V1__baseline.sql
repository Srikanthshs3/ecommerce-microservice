-- shipping-service baseline (MySQL). Version 1 = the complete schema as it is today (see user-service V1 for the rules).

CREATE TABLE shipments (
    id                BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id          BIGINT         NOT NULL,
    tracking_number   VARCHAR(255)   NOT NULL,
    carrier           VARCHAR(255)   NOT NULL,
    status            VARCHAR(30)    NOT NULL,
    created_at        DATETIME(6)    NOT NULL,
    user_id           BIGINT,
    seller_id         BIGINT,
    shipping_pincode  VARCHAR(6),
    shipping_address  VARCHAR(1000),
    payment_method    VARCHAR(20),
    cash_to_collect   DECIMAL(12, 2),
    updated_at        DATETIME(6),
    agent_id          BIGINT,
    picked_up_at      DATETIME(6),
    delivered_at      DATETIME(6),
    otp_version       INT            NOT NULL DEFAULT 0,
    otp_attempts      INT            NOT NULL DEFAULT 0,
    failed_attempts   INT            NOT NULL DEFAULT 0,
    cash_collected    BIT(1),
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_shipments_order    UNIQUE (order_id),
    CONSTRAINT uk_shipments_tracking UNIQUE (tracking_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_shipments_agent_status ON shipments (agent_id, status);
CREATE INDEX idx_shipments_pincode_status ON shipments (shipping_pincode, status);

CREATE TABLE shipment_events (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    shipment_id  BIGINT       NOT NULL,
    order_id     BIGINT       NOT NULL,
    status       VARCHAR(30)  NOT NULL,
    note         VARCHAR(500),
    actor        VARCHAR(60),
    created_at   DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_shipment_events_order ON shipment_events (order_id);

-- Transactional outbox of this service (ShippingOutboxPoller publishes these rows to Kafka).
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
