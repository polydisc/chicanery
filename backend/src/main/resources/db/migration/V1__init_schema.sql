-- Initial schema. Flyway (run on app startup, see DbMigrationComponent) is the
-- source of truth for the backend_db schema. The database itself is created by
-- the Postgres container (POSTGRES_DB=backend_db).

-- UserState
CREATE TYPE UserState AS ENUM (
    'PENDING',
    'ACTIVE',
    'BLOCKED',
    'DELETED'
);

-- Users
CREATE TABLE users
(
    id            serial not null,
    PRIMARY KEY (id),
    UNIQUE (email_address),
    state         UserState,
    last_name     text,
    first_name    text,
    email_address text not null,
    password      text not null,
    postal_code   text,
    address       text,
    access_token  text not null,
    updated_at    date,
    created_at    date
);

-- Payments
CREATE TABLE credit_cards
(
    user_id         int,
    card_number     text,
    card_holder     text,
    expiring_year   int,
    expiring_month  int,
    cvc             int,
    billing_address text,
    updated_at      date,
    created_at      date,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE bank_accounts
(
    user_id     int,
    bank_name   text,
    bank_number text,
    updated_at  date,
    created_at  date,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Products
CREATE TABLE products
(
    id                serial not null,
    PRIMARY KEY (id),
    product_name      text,
    product_category  text,
    price_jpy         int,
    product_image_url text,
    updated_at        date,
    created_at        date
);

CREATE TABLE reviews
(
    id         serial not null,
    PRIMARY KEY (id),
    product_id int,
    user_id    int,
    rating     int,
    review     text,
    updated_at date,
    created_at date,
    FOREIGN KEY (product_id) REFERENCES products (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Carts
CREATE TABLE carts
(
    id         serial not null,
    PRIMARY KEY (id),
    user_id    int,
    session_id text,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Order status
CREATE TYPE OrderStatus AS ENUM (
    'NEW',
    'PROCESSING',
    'SHIPPED',
    'HOLD',
    'DELIVERED',
    'CANCELLED'
);

-- Orders
CREATE TABLE orders
(
    id               serial not null,
    PRIMARY KEY (id),
    user_id          int,
    shipping_address text,
    totalPrice       int,
    order_date       date,
    shipped_date     date,
    order_status     OrderStatus,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE line_item
(
    cart_id    int,
    product_id int,
    order_id   int,
    price      int,
    quantity   int,
    FOREIGN KEY (cart_id) REFERENCES carts (id),
    FOREIGN KEY (product_id) REFERENCES products (id),
    FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE TABLE payment
(
    id       serial not null,
    PRIMARY KEY (id),
    user_id  int,
    order_id int,
    details  text,
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (order_id) REFERENCES orders (id)
);
