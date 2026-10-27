-- The outbox payload (a JSON snapshot of an order / notification) can be far longer than 255 characters. On databases that
-- Hibernate created before Flyway these two columns are VARCHAR(255); on newer ones they are already TEXT (then this
-- statement changes nothing).
ALTER TABLE outbox_events ALTER COLUMN json_payload TYPE TEXT;
ALTER TABLE outbox_events ALTER COLUMN last_error   TYPE TEXT;
