-- P3 顾客与购物车基线。顾客会话与员工会话使用不同表和令牌前缀。
CREATE TABLE customer_account (
    id UUID PRIMARY KEY,
    phone VARCHAR(16) NOT NULL UNIQUE,
    display_name VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    security_version BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE customer_session (
    token_hash VARCHAR(64) PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customer_account (id),
    security_version BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX customer_session_customer_idx ON customer_session (customer_id);
CREATE INDEX customer_session_expiry_idx ON customer_session (expires_at);

CREATE TABLE customer_address (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customer_account (id),
    label VARCHAR(20) NOT NULL,
    recipient_name VARCHAR(50) NOT NULL,
    phone VARCHAR(16) NOT NULL,
    province VARCHAR(50) NOT NULL,
    city VARCHAR(50) NOT NULL,
    district VARCHAR(50) NOT NULL,
    detail VARCHAR(200) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX customer_address_owner_idx ON customer_address (customer_id, created_at, id);
CREATE UNIQUE INDEX customer_address_default_idx ON customer_address (customer_id)
    WHERE is_default;

CREATE TABLE cart (
    customer_id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE cart_item (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES cart (customer_id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    product_kind VARCHAR(16) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    unit_price NUMERIC(8, 2) NOT NULL CHECK (unit_price > 0),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    selections JSONB NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0)
);
CREATE INDEX cart_item_owner_idx ON cart_item (customer_id, id);
