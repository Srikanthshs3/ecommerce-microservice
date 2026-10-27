-- Run ONCE on shipping_db (MySQL) before starting the new shipping-service.
-- Hibernate 6 created shipments.status as a MySQL ENUM('CREATED','IN_TRANSIT','DELIVERED'); new statuses would be rejected.
ALTER TABLE shipments MODIFY COLUMN status VARCHAR(30) NOT NULL;
