-- Variant (colour / size ...) chosen for an order line.
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS variant_id VARCHAR(40);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS variant_label VARCHAR(200);
