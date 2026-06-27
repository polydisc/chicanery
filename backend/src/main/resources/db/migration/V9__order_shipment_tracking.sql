-- R9 shipment tracking + ETA. The `shipped_date` column already exists (V1);
-- add the tracking number, carrier and estimated-delivery (ETA) columns that an
-- admin fills in when marking an order SHIPPED. All nullable: they are absent
-- until the order ships.
ALTER TABLE orders ADD COLUMN tracking_number text;
ALTER TABLE orders ADD COLUMN carrier text;
ALTER TABLE orders ADD COLUMN estimated_delivery_date date;
