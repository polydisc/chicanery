-- Additional development/test seed data, layered on top of V2 so we never
-- rewrite an already-applied migration. Gives the catalog images and adds
-- reviews so the frontend has realistic content to exercise locally.
--
-- Image URLs are root-relative (e.g. /combo_examples/...): the frontend serves
-- its public/ assets at the site root, so `next/image` loads them from whatever
-- origin the app runs on. Spaces are %20-encoded for valid URLs.

-- Themed fruit-salad combos with images (the catalog the home page browses).
INSERT INTO products (product_name, product_category, price_jpy, product_image_url, created_at, updated_at)
VALUES
    ('Honey Lime Combo', 'combo', 2000,
     '/combo_examples/Honey-Lime-Peach-Fruit-Salad-3-725x725-1-removebg-preview%201.png',
     current_date, current_date),
    ('Glowing Berry Combo', 'combo', 2400,
     '/combo_examples/Glowing-Berry-Fruit-Salad-8-720x720-removebg-preview%201.png',
     current_date, current_date),
    ('Tropical Combo', 'combo', 2800,
     '/combo_examples/Best-Ever-Tropical-Fruit-Salad8-WIDE-removebg-preview%201.png',
     current_date, current_date),
    ('Quinoa Red Fruit Combo', 'combo', 2200,
     '/combo_examples/breakfast-quinoa-and-red-fruit-salad-134061-1-removebg-preview%202.png',
     current_date, current_date);

-- Give the original single-fruit products (seeded imageless in V2) artwork too,
-- so every catalog tile renders. Only touch rows that still lack an image.
UPDATE products SET product_image_url =
    '/combo_examples/Glowing-Berry-Fruit-Salad-8-720x720-removebg-preview%201.png'
    WHERE product_name = 'Strawberry' AND product_image_url IS NULL;
UPDATE products SET product_image_url =
    '/combo_examples/Honey-Lime-Peach-Fruit-Salad-3-725x725-1-removebg-preview%201.png'
    WHERE product_name = 'Lemon' AND product_image_url IS NULL;
UPDATE products SET product_image_url =
    '/combo_examples/Best-Ever-Tropical-Fruit-Salad8-WIDE-removebg-preview%201.png'
    WHERE product_name = 'Grape' AND product_image_url IS NULL;
UPDATE products SET product_image_url =
    '/combo_examples/breakfast-quinoa-and-red-fruit-salad-134061-1-removebg-preview%201.png'
    WHERE product_name = 'Banana' AND product_image_url IS NULL;

-- Reviews authored by the seeded dev user. Joining by product name / email keeps
-- this robust to whatever serial ids the rows above received.
INSERT INTO reviews (product_id, user_id, rating, review, created_at, updated_at)
SELECT p.id, u.id, v.rating, v.review, current_date, current_date
FROM (
    VALUES
        ('Honey Lime Combo', 5, 'Refreshing and not too sweet, my go-to order.'),
        ('Honey Lime Combo', 4, 'Great balance of lime and honey.'),
        ('Glowing Berry Combo', 5, 'So many berries, absolutely loved it.'),
        ('Glowing Berry Combo', 4, 'Fresh and generous portion.'),
        ('Tropical Combo', 4, 'Tastes like a holiday in a bowl.'),
        ('Quinoa Red Fruit Combo', 5, 'Filling brunch option and very fresh.'),
        ('Banana', 3, 'Just a banana, but a good one.')
) AS v(product_name, rating, review)
JOIN products p ON p.product_name = v.product_name
JOIN users u ON u.email_address = 'john.doe@example.com';
