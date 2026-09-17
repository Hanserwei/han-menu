CREATE TABLE catalog_dish (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (length(trim(name)) > 0),
    price NUMERIC(10, 2) NOT NULL CHECK (price > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

-- Separate ownership: no cross-module join or foreign key to catalog_dish.
CREATE TABLE menu_entry (
    dish_id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price NUMERIC(10, 2) NOT NULL CHECK (price > 0)
);
