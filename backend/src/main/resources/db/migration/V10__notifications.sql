-- R8 in-app notifications. A notification is created (in the same transaction)
-- whenever an order changes state: placed, paid, shipped, delivered, cancelled.
-- Stored per recipient (the order's owner) and fetched/marked-read by the SPA.
CREATE TYPE NotificationType AS ENUM (
    'ORDER_PLACED',
    'PAID',
    'SHIPPED',
    'DELIVERED',
    'CANCELLED'
);

CREATE TABLE notifications
(
    id         bigserial   NOT NULL PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id),
    order_id   bigint      REFERENCES orders (id),
    type       NotificationType NOT NULL,
    message    text        NOT NULL,
    read       boolean     NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- The common read path is "this user's notifications, unread first / newest
-- first", and the unread-count badge filters on (user_id, read).
CREATE INDEX notifications_user_idx ON notifications (user_id, read);
