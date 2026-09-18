-- 目录修订行串行化低频后台写入；业务状态和缓存代际在同一事务变更。
CREATE TABLE catalog_revision (id INTEGER PRIMARY KEY CHECK (id = 1), revision BIGINT NOT NULL);
INSERT INTO catalog_revision VALUES (1, 0);

CREATE TABLE catalog_category (
    id UUID PRIMARY KEY,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('DISH', 'SET_MEAL')),
    name VARCHAR(50) NOT NULL,
    sort_order INTEGER NOT NULL CHECK (sort_order BETWEEN 0 AND 10000),
    enabled BOOLEAN NOT NULL,
    version BIGINT NOT NULL
);
CREATE UNIQUE INDEX catalog_category_name_idx ON catalog_category (kind, lower(name));

CREATE TABLE catalog_image (
    id UUID PRIMARY KEY,
    object_key VARCHAR(200) NOT NULL UNIQUE,
    media_type VARCHAR(32) NOT NULL,
    size BIGINT NOT NULL CHECK (size > 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE catalog_product (
    id UUID PRIMARY KEY,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('DISH', 'SET_MEAL')),
    category_id UUID NOT NULL REFERENCES catalog_category (id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    price NUMERIC(8, 2) NOT NULL CHECK (price > 0),
    image_id UUID REFERENCES catalog_image (id),
    flavors JSONB NOT NULL,
    on_sale BOOLEAN NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX catalog_product_name_idx ON catalog_product (category_id, lower(name));
CREATE INDEX catalog_product_listing_idx ON catalog_product (on_sale, category_id, created_at, id);

CREATE TABLE catalog_meal_component (
    meal_id UUID NOT NULL REFERENCES catalog_product (id),
    position INTEGER NOT NULL,
    dish_id UUID NOT NULL REFERENCES catalog_product (id),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    selections JSONB NOT NULL,
    PRIMARY KEY (meal_id, position),
    UNIQUE (meal_id, dish_id),
    CHECK (meal_id <> dish_id)
);
CREATE INDEX catalog_meal_dish_idx ON catalog_meal_component (dish_id);

-- 单店状态默认关闭；配置完名称、电话和地址后由管理员明确开店。
CREATE TABLE shop_profile (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(16) NOT NULL,
    address VARCHAR(300) NOT NULL,
    open BOOLEAN NOT NULL,
    version BIGINT NOT NULL
);
INSERT INTO shop_profile VALUES (1, 'Han Menu', '', '', FALSE, 0);
