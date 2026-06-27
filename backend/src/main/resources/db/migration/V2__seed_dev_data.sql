-- Development seed data. The password below is the bcrypt hash of "password"
-- (so you can log in as john.doe@example.com / password).

INSERT INTO users (state, first_name, last_name, email_address, password, access_token, created_at, updated_at)
VALUES ('ACTIVE', 'John', 'Doe', 'john.doe@example.com',
        '$2a$12$AkACHvxNuQZIz5.ru1Gc0uyBjDxxuyu.JWqJDWfvLw6q229aQBfIG',
        'token', current_date, current_date);

INSERT INTO products (product_name, product_category, price_jpy, created_at, updated_at)
VALUES ('Banana', 'fruit', 200, current_date, current_date),
       ('Strawberry', 'fruit', 800, current_date, current_date),
       ('Lemon', 'fruit', 500, current_date, current_date),
       ('Grape', 'fruit', 700, current_date, current_date);
