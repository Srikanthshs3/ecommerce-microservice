-- Coupon support: which coupon a checkout used, this order's share of the discount, and the id shared by the
-- orders (one per seller) of one checkout.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS coupon_code     VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS coupon_discount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS checkout_ref    VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_orders_checkout_ref ON orders (checkout_ref);
