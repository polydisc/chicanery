-- RBAC: add a user role and seed an admin account.

CREATE TYPE UserRole AS ENUM ('USER', 'ADMIN');

ALTER TABLE users ADD COLUMN role UserRole NOT NULL DEFAULT 'USER';

-- Seed a DEVELOPMENT admin (admin@example.com / password); the hash is the same
-- bcrypt("password") as the V2 dev user. This is for local/dev use — in a real
-- deployment, change this password (or remove the row) immediately.
INSERT INTO users
    (state, role, first_name, last_name, email_address, password,
     access_token, created_at, updated_at)
VALUES
    ('ACTIVE', 'ADMIN', 'Admin', 'User', 'admin@example.com',
     '$2a$12$AkACHvxNuQZIz5.ru1Gc0uyBjDxxuyu.JWqJDWfvLw6q229aQBfIG',
     'token', current_date, current_date);
