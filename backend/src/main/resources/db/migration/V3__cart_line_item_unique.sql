-- A product appears at most once per active cart (line items not yet ordered),
-- which lets add-to-cart be an atomic `INSERT ... ON CONFLICT DO UPDATE` upsert
-- instead of a racy update-then-insert.
CREATE UNIQUE INDEX line_item_cart_product_uniq
    ON line_item (cart_id, product_id)
    WHERE order_id IS NULL;
