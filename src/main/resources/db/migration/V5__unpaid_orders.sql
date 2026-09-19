-- 订单只保存跨模块标识，外键仅连接订单与订单明细。
CREATE TABLE ordering_order (
    id uuid PRIMARY KEY,
    customer_id uuid NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_fingerprint varchar(64) NOT NULL,
    source_id uuid NOT NULL,
    source_version bigint NOT NULL CHECK (source_version >= 0),
    recipient_name varchar(50) NOT NULL,
    phone varchar(20) NOT NULL,
    province varchar(50) NOT NULL,
    city varchar(50) NOT NULL,
    district varchar(50) NOT NULL,
    detail varchar(200) NOT NULL,
    total numeric(16,2) NOT NULL CHECK (total > 0),
    status varchar(20) NOT NULL CHECK (status IN ('UNPAID', 'CANCELLED')),
    version bigint NOT NULL CHECK (version >= 0),
    created_at timestamptz NOT NULL,
    cancelled_at timestamptz,
    CONSTRAINT ordering_submit_key UNIQUE (customer_id, idempotency_key),
    CONSTRAINT ordering_cancel_state CHECK (
      (status = 'UNPAID' AND cancelled_at IS NULL)
      OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_at >= created_at))
);
CREATE INDEX ordering_customer_history ON ordering_order (customer_id, created_at DESC, id DESC);
CREATE TABLE ordering_line (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES ordering_order(id),
    position integer NOT NULL CHECK (position >= 0 AND position < 50),
    product_id uuid NOT NULL,
    kind varchar(20) NOT NULL CHECK (kind IN ('DISH', 'SET_MEAL')),
    name varchar(100) NOT NULL,
    unit_price numeric(12,2) NOT NULL CHECK (unit_price > 0),
    quantity integer NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    selections jsonb NOT NULL,
    components jsonb NOT NULL,
    UNIQUE (order_id, position)
);
