-- R6 payment hardening. Track a payment's status so cancelling a paid
-- (PROCESSING) order can record a refund. Real gateways are out of scope — this
-- is a stub refund (status flip), not an actual money movement. Existing rows
-- were all successful charges, so they default to PAID.
ALTER TABLE payment ADD COLUMN status text NOT NULL DEFAULT 'PAID';
