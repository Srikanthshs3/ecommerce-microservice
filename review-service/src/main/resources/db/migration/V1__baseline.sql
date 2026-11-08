-- review-service baseline (MySQL). Version 1 = the complete schema as it is today (see user-service V1 for the rules).

CREATE TABLE reviews (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id         VARCHAR(64)   NOT NULL,
    user_id            BIGINT        NOT NULL,
    user_name          VARCHAR(100)  NOT NULL,
    rating             INT           NOT NULL,
    title              VARCHAR(120),
    comment            VARCHAR(2000),
    verified_purchase  BIT(1)        NOT NULL DEFAULT 0,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    CONSTRAINT uk_review_product_user UNIQUE (product_id, user_id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_review_product_created ON reviews (product_id, created_at);
CREATE INDEX idx_review_product_rating  ON reviews (product_id, rating);

CREATE TABLE review_images (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    review_id     BIGINT       NOT NULL,
    file_name     VARCHAR(80)  NOT NULL,
    content_type  VARCHAR(50)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    position      INT          NOT NULL,
    CONSTRAINT uk_review_images_file UNIQUE (file_name),
    CONSTRAINT fk_review_images_review FOREIGN KEY (review_id) REFERENCES reviews (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_review_images_review ON review_images (review_id);

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
