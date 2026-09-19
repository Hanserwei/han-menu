-- P5 在已有订单快照上增加支付引用和履约事实，不重建或清空 P4 数据。
ALTER TABLE ordering_order DROP CONSTRAINT ordering_order_status_check;
ALTER TABLE ordering_order DROP CONSTRAINT ordering_cancel_state;
ALTER TABLE ordering_order ADD COLUMN payment_id uuid UNIQUE;
ALTER TABLE ordering_order ADD COLUMN paid_at timestamptz;
ALTER TABLE ordering_order ADD COLUMN accepted_at timestamptz;
ALTER TABLE ordering_order ADD COLUMN delivered_at timestamptz;
ALTER TABLE ordering_order ADD COLUMN completed_at timestamptz;
ALTER TABLE ordering_order ADD COLUMN cancel_reason varchar(32);
ALTER TABLE ordering_order ADD COLUMN refund_status varchar(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE ordering_order ADD COLUMN refund_id uuid;
UPDATE ordering_order SET cancel_reason = 'CUSTOMER' WHERE status = 'CANCELLED';
ALTER TABLE ordering_order ADD CONSTRAINT ordering_status CHECK (
  status IN ('UNPAID', 'PAID', 'ACCEPTED', 'DELIVERING', 'COMPLETED', 'CANCELLING', 'REFUNDING', 'CANCELLED'));
ALTER TABLE ordering_order ADD CONSTRAINT ordering_cancel_state CHECK (
  (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_at >= created_at)
  OR (status <> 'CANCELLED' AND cancelled_at IS NULL));
ALTER TABLE ordering_order ADD CONSTRAINT ordering_refund_status CHECK (refund_status IN ('NONE', 'PENDING', 'SUCCEEDED'));
ALTER TABLE ordering_order ADD CONSTRAINT ordering_fulfillment_facts CHECK (
  (status NOT IN ('PAID', 'ACCEPTED', 'DELIVERING', 'COMPLETED') OR paid_at IS NOT NULL)
  AND (status NOT IN ('ACCEPTED', 'DELIVERING', 'COMPLETED') OR accepted_at IS NOT NULL)
  AND (status NOT IN ('DELIVERING', 'COMPLETED') OR delivered_at IS NOT NULL)
  AND (status <> 'COMPLETED' OR completed_at IS NOT NULL));
CREATE INDEX ordering_expiration ON ordering_order (status, created_at, id);

-- 支付只保存不透明业务标识，不关联 ordering/customer 业务表。
CREATE TABLE payment_intent (
  id uuid PRIMARY KEY,
  business_ref uuid NOT NULL UNIQUE,
  customer_id uuid NOT NULL,
  amount numeric(16,2) NOT NULL CHECK (amount > 0),
  idempotency_key varchar(128) NOT NULL,
  fingerprint varchar(64) NOT NULL,
  created_at timestamptz NOT NULL,
  expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
  status varchar(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'CLOSED')),
  trade_no varchar(64) UNIQUE,
  paid_at timestamptz,
  close_requested boolean NOT NULL,
  next_attempt_at timestamptz,
  last_failure varchar(64),
  version bigint NOT NULL CHECK (version >= 0),
  UNIQUE (customer_id, idempotency_key),
  CHECK ((status = 'SUCCEEDED' AND paid_at IS NOT NULL AND trade_no IS NOT NULL)
    OR (status <> 'SUCCEEDED' AND paid_at IS NULL))
);
CREATE INDEX payment_due ON payment_intent (next_attempt_at, id) WHERE next_attempt_at IS NOT NULL;
CREATE TABLE payment_refund (
  id uuid PRIMARY KEY,
  payment_id uuid NOT NULL UNIQUE REFERENCES payment_intent(id),
  business_ref uuid NOT NULL,
  customer_id uuid NOT NULL,
  trade_no varchar(64) NOT NULL,
  amount numeric(16,2) NOT NULL CHECK (amount > 0),
  created_at timestamptz NOT NULL,
  status varchar(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED')),
  confirmed_at timestamptz,
  next_attempt_at timestamptz,
  last_failure varchar(64),
  version bigint NOT NULL CHECK (version >= 0),
  CHECK ((status = 'SUCCEEDED' AND confirmed_at IS NOT NULL AND next_attempt_at IS NULL)
    OR (status = 'PENDING' AND confirmed_at IS NULL))
);
CREATE INDEX refund_due ON payment_refund (next_attempt_at, id) WHERE next_attempt_at IS NOT NULL;
