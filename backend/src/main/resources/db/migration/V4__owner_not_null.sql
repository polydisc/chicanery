-- Ownership is now always set (carts/orders are created for the authenticated
-- user), and every authorization check filters on these columns. Enforce NOT
-- NULL so a future code path can't silently create an unowned, world-readable
-- cart or order.
ALTER TABLE carts ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE orders ALTER COLUMN user_id SET NOT NULL;
