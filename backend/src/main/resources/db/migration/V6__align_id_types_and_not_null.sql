-- Surfaced by the doobie query analysis (QueryCheckSpec) running in CI:
--   (1) id / foreign-key columns are `serial`/`int` (JDBC INTEGER) while the
--       domain & API model them as `Long` (the API uses int64);
--   (2) some always-present columns are nullable but read into non-Option
--       Scala types, which would fail at runtime on a NULL.
-- This aligns the id/FK columns to `bigint` and makes those columns NOT NULL.

-- 1. Drop foreign keys so referenced/referencing column types can change.
ALTER TABLE credit_cards DROP CONSTRAINT IF EXISTS credit_cards_user_id_fkey;
ALTER TABLE bank_accounts DROP CONSTRAINT IF EXISTS bank_accounts_user_id_fkey;
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_product_id_fkey;
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_user_id_fkey;
ALTER TABLE carts DROP CONSTRAINT IF EXISTS carts_user_id_fkey;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_user_id_fkey;
ALTER TABLE line_item DROP CONSTRAINT IF EXISTS line_item_cart_id_fkey;
ALTER TABLE line_item DROP CONSTRAINT IF EXISTS line_item_product_id_fkey;
ALTER TABLE line_item DROP CONSTRAINT IF EXISTS line_item_order_id_fkey;
ALTER TABLE payment DROP CONSTRAINT IF EXISTS payment_user_id_fkey;
ALTER TABLE payment DROP CONSTRAINT IF EXISTS payment_order_id_fkey;

-- 2. Primary-key id columns -> bigint.
ALTER TABLE users ALTER COLUMN id TYPE bigint;
ALTER TABLE products ALTER COLUMN id TYPE bigint;
ALTER TABLE reviews ALTER COLUMN id TYPE bigint;
ALTER TABLE carts ALTER COLUMN id TYPE bigint;
ALTER TABLE orders ALTER COLUMN id TYPE bigint;
ALTER TABLE payment ALTER COLUMN id TYPE bigint;

-- 3. Foreign-key columns -> bigint.
ALTER TABLE credit_cards ALTER COLUMN user_id TYPE bigint;
ALTER TABLE bank_accounts ALTER COLUMN user_id TYPE bigint;
ALTER TABLE reviews ALTER COLUMN product_id TYPE bigint;
ALTER TABLE reviews ALTER COLUMN user_id TYPE bigint;
ALTER TABLE carts ALTER COLUMN user_id TYPE bigint;
ALTER TABLE orders ALTER COLUMN user_id TYPE bigint;
ALTER TABLE line_item ALTER COLUMN cart_id TYPE bigint;
ALTER TABLE line_item ALTER COLUMN product_id TYPE bigint;
ALTER TABLE line_item ALTER COLUMN order_id TYPE bigint;
ALTER TABLE payment ALTER COLUMN user_id TYPE bigint;
ALTER TABLE payment ALTER COLUMN order_id TYPE bigint;

-- 4. Re-add the foreign keys (both sides are bigint now).
ALTER TABLE credit_cards ADD CONSTRAINT credit_cards_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE bank_accounts ADD CONSTRAINT bank_accounts_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE reviews ADD CONSTRAINT reviews_product_id_fkey FOREIGN KEY (product_id) REFERENCES products (id);
ALTER TABLE reviews ADD CONSTRAINT reviews_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE carts ADD CONSTRAINT carts_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE orders ADD CONSTRAINT orders_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE line_item ADD CONSTRAINT line_item_cart_id_fkey FOREIGN KEY (cart_id) REFERENCES carts (id);
ALTER TABLE line_item ADD CONSTRAINT line_item_product_id_fkey FOREIGN KEY (product_id) REFERENCES products (id);
ALTER TABLE line_item ADD CONSTRAINT line_item_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders (id);
ALTER TABLE payment ADD CONSTRAINT payment_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE payment ADD CONSTRAINT payment_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders (id);

-- 5. Backfill, then enforce NOT NULL on columns that are always present and are
--    read into non-Option Scala types.
UPDATE products SET product_name = '' WHERE product_name IS NULL;
UPDATE products SET price_jpy = 0 WHERE price_jpy IS NULL;
ALTER TABLE products ALTER COLUMN product_name SET NOT NULL;
ALTER TABLE products ALTER COLUMN price_jpy SET NOT NULL;

UPDATE line_item SET price = 0 WHERE price IS NULL;
UPDATE line_item SET quantity = 0 WHERE quantity IS NULL;
ALTER TABLE line_item ALTER COLUMN product_id SET NOT NULL;
ALTER TABLE line_item ALTER COLUMN price SET NOT NULL;
ALTER TABLE line_item ALTER COLUMN quantity SET NOT NULL;
