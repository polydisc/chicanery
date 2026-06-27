-- R6 follow-up: constrain payment.status to the values the code actually uses,
-- so an invalid status can't be written even outside the application.
ALTER TABLE payment
    ADD CONSTRAINT payment_status_check CHECK (status IN ('PAID', 'REFUNDED'));
