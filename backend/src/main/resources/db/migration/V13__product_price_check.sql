-- Robustness: a product price can never be negative (the service validates this
-- too; the constraint enforces it at the database level as well).
ALTER TABLE products
    ADD CONSTRAINT products_price_jpy_check CHECK (price_jpy >= 0);
