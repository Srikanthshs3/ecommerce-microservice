-- New order statuses (READY_TO_SHIP, OUT_FOR_DELIVERY). Newer Hibernate versions create a CHECK constraint listing the
-- enum values when it generates the table; drop it so the new values can be stored.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;

-- (an earlier version of this file also had "ALTER TABLE shipments MODIFY COLUMN ...": that is MySQL syntax and the
-- shipments table lives in shipping-service's own database, so it could never run here and was removed.)
