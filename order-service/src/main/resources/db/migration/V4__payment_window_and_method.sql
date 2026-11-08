-- Amazon-style checkout: the customer pays once per checkout, within a time window.
--   payment_method : RAZORPAY / STRIPE / COD (null until paid or COD chosen)
--   expires_at     : last moment to pay; stock is reserved until then, afterwards the order is cancelled
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS expires_at     TIMESTAMPTZ;

-- Every order belongs to a checkout now (before, only coupon orders had one). Give old orders their own.
UPDATE orders SET checkout_ref = 'legacy-' || id WHERE checkout_ref IS NULL;

CREATE INDEX IF NOT EXISTS idx_orders_status_expires ON orders (status, expires_at);
