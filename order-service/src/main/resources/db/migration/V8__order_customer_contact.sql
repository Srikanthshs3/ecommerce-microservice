-- Snapshot of who to e-mail about this order (taken when it is placed). Old rows stay NULL: they simply get no e-mails.
ALTER TABLE orders ADD COLUMN customer_email VARCHAR(255) NULL;
ALTER TABLE orders ADD COLUMN customer_name  VARCHAR(255) NULL;
