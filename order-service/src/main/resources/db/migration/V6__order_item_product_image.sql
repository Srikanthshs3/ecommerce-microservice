-- Picture snapshot per order line (shown in My orders, seller orders and at payment).
ALTER TABLE order_items ADD COLUMN product_image VARCHAR(500) NULL;
