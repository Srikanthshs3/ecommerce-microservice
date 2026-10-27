-- user-service baseline (MySQL). Version 1 = the complete schema as it is today.
-- An EXISTING database (created earlier by Hibernate) is adopted at version 1 by Flyway (baseline-on-migrate) and this
-- file is skipped; an EMPTY database (new machine) runs it. Every later change = a new V2__..., V3__... file.
-- Never edit a migration that has already been applied anywhere: add a new one.

CREATE TABLE users (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email               VARCHAR(254) NOT NULL,
    name                VARCHAR(255),
    phone_number        VARCHAR(255),
    password            VARCHAR(255) NOT NULL,
    email_verified      BIT(1)       NOT NULL DEFAULT 0,
    created_at          DATETIME(6),
    updated_at          DATETIME(6),
    deleted_at          DATETIME(6),                                   -- soft delete (@SQLDelete)
    account_non_locked  BIT(1)       NOT NULL DEFAULT 1,
    failed_attempt_count INT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_role (
    id    BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(50) NOT NULL,
    CONSTRAINT uk_user_role_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Reference data: the roles the application knows. (Used to live in data.sql, which only ran when configured to.)
INSERT INTO user_role (id, name) VALUES
    (1, 'CUSTOMER'), (2, 'SELLER'), (3, 'ADMIN'), (4, 'DELIVERY_PARTNER'), (5, 'SUPPORT_AGENT');

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES user_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The three profile tables share the primary key of "users" (@MapsId): the key column is called user_id.
CREATE TABLE customer_profile (
    user_id   BIGINT NOT NULL PRIMARY KEY,
    full_name VARCHAR(255),
    CONSTRAINT fk_customer_profile_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE seller_profile (
    user_id              BIGINT       NOT NULL PRIMARY KEY,
    kyc_status           VARCHAR(20),
    status_reason        VARCHAR(500),
    kyc_reviewed_at      DATETIME(6),
    kyc_reviewed_by      BIGINT,
    account_number       VARCHAR(255),                                  -- encrypted at rest (EncryptedStringConverter)
    account_holder_name  VARCHAR(255),
    ifsc_code            VARCHAR(255),
    business_name        VARCHAR(255),
    legal_name           VARCHAR(255),
    business_address     VARCHAR(500),
    business_phone       VARCHAR(255),
    business_email       VARCHAR(255),
    logo                 VARCHAR(500),
    banner               VARCHAR(500),
    gstin                VARCHAR(15),
    pan                  VARCHAR(10),
    terms_accepted_at    DATETIME(6),
    created_at           DATETIME(6),
    updated_at           DATETIME(6),
    CONSTRAINT uk_seller_gstin UNIQUE (gstin),
    CONSTRAINT fk_seller_profile_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_seller_kyc_status ON seller_profile (kyc_status);

CREATE TABLE delivery_agent_profile (
    user_id             BIGINT       NOT NULL PRIMARY KEY,
    status              VARCHAR(20)  NOT NULL,
    status_reason       VARCHAR(500),
    phone               VARCHAR(15)  NOT NULL,
    vehicle_type        VARCHAR(20)  NOT NULL,
    vehicle_number      VARCHAR(20),
    id_proof_type       VARCHAR(20)  NOT NULL,
    id_proof_number     VARCHAR(255) NOT NULL,                          -- encrypted at rest
    city                VARCHAR(60)  NOT NULL,
    serviceable_pincodes VARCHAR(300) NOT NULL,
    on_duty             BIT(1)       NOT NULL DEFAULT 0,
    reviewed_at         DATETIME(6),
    reviewed_by         BIGINT,
    created_at          DATETIME(6),
    updated_at          DATETIME(6),
    CONSTRAINT fk_agent_profile_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_agent_status ON delivery_agent_profile (status);

CREATE TABLE address (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id    BIGINT,
    seller_id      BIGINT,
    recipient_name VARCHAR(255),
    street_line1   VARCHAR(255),
    street_line2   VARCHAR(255),
    city           VARCHAR(255),
    state          VARCHAR(255),
    postal_code    VARCHAR(255),
    country        VARCHAR(255),
    is_default     BIT(1)       NOT NULL DEFAULT 0,
    address_type   VARCHAR(20),
    CONSTRAINT fk_address_customer FOREIGN KEY (customer_id) REFERENCES customer_profile (user_id),
    CONSTRAINT fk_address_seller   FOREIGN KEY (seller_id)   REFERENCES seller_profile (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_address_customer ON address (customer_id);
CREATE INDEX idx_address_seller   ON address (seller_id);

CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    token       VARCHAR(128) NOT NULL,                                  -- SHA-256 hash of the token, never the token itself
    user_id     BIGINT       NOT NULL,
    family_id   VARCHAR(36),
    expiry_date DATETIME(6)  NOT NULL,
    revoked     BIT(1)       NOT NULL DEFAULT 0,
    created_at  DATETIME(6),
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_refresh_token_user   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_token_family ON refresh_tokens (family_id);

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
