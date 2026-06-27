-- Record how much was paid so payments are auditable (snapshot of the order
-- total at payment time).
ALTER TABLE payment ADD COLUMN amount int;
