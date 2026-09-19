-- P6 增量增加催单、通知和经营投影，所有外键均限定在各模块内部。
ALTER TABLE ordering_order ADD COLUMN reminder_count integer NOT NULL DEFAULT 0 CHECK (reminder_count >= 0);
ALTER TABLE ordering_order ADD COLUMN last_reminded_at timestamptz;
ALTER TABLE ordering_order ADD CONSTRAINT ordering_reminder_state CHECK (
  (reminder_count = 0 AND last_reminded_at IS NULL) OR (reminder_count > 0 AND last_reminded_at IS NOT NULL));

CREATE TABLE notification_feed (id integer PRIMARY KEY CHECK (id = 1), sequence bigint NOT NULL CHECK (sequence >= 0));
INSERT INTO notification_feed (id, sequence) VALUES (1, 0);
CREATE TABLE notification_notice (
  id uuid PRIMARY KEY,
  sequence bigint NOT NULL UNIQUE CHECK (sequence > 0),
  order_id uuid NOT NULL,
  kind varchar(24) NOT NULL CHECK (kind IN ('NEW_ORDER', 'ORDER_REMINDER')),
  occurred_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL,
  status varchar(24) NOT NULL CHECK (status IN ('PENDING', 'IN_FLIGHT', 'DELIVERED', 'EXHAUSTED')),
  attempts integer NOT NULL CHECK (attempts >= 0),
  failures integer NOT NULL CHECK (failures BETWEEN 0 AND 5),
  next_attempt_at timestamptz,
  active_attempt_id uuid,
  last_failure varchar(64),
  version bigint NOT NULL,
  CHECK ((status IN ('PENDING', 'IN_FLIGHT') AND next_attempt_at IS NOT NULL)
      OR (status IN ('DELIVERED', 'EXHAUSTED') AND next_attempt_at IS NULL)),
  CHECK ((status = 'IN_FLIGHT' AND active_attempt_id IS NOT NULL) OR (status <> 'IN_FLIGHT' AND active_attempt_id IS NULL))
);
CREATE INDEX notification_due ON notification_notice (next_attempt_at, sequence) WHERE next_attempt_at IS NOT NULL;
CREATE TABLE notification_attempt (
  id uuid PRIMARY KEY,
  notice_id uuid NOT NULL REFERENCES notification_notice(id),
  started_at timestamptz NOT NULL,
  finished_at timestamptz,
  status varchar(24) NOT NULL CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'SUPERSEDED')),
  sent integer NOT NULL DEFAULT 0 CHECK (sent >= 0),
  failed integer NOT NULL DEFAULT 0 CHECK (failed >= 0),
  failure varchar(64)
);
CREATE INDEX notification_attempt_history ON notification_attempt (notice_id, started_at DESC, id DESC);
CREATE TABLE notification_receipt (
  employee_id uuid PRIMARY KEY,
  sequence bigint NOT NULL CHECK (sequence >= 0),
  version bigint NOT NULL,
  updated_at timestamptz NOT NULL
);
CREATE TABLE notification_ticket (
  session_hash varchar(64) PRIMARY KEY,
  ticket_hash varchar(64) NOT NULL UNIQUE,
  employee_id uuid NOT NULL,
  security_version bigint NOT NULL,
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz,
  version bigint NOT NULL
);
CREATE INDEX notification_ticket_expiry ON notification_ticket (expires_at);

CREATE TABLE reporting_projection (
  id integer PRIMARY KEY CHECK (id = 1),
  version bigint NOT NULL DEFAULT 0,
  generation bigint NOT NULL DEFAULT 0,
  revision bigint NOT NULL DEFAULT 0,
  initialized boolean NOT NULL DEFAULT false,
  updated_at timestamptz NOT NULL,
  rebuilt_at timestamptz
);
INSERT INTO reporting_projection(id, updated_at) VALUES (1, '1970-01-01T00:00:00Z');
CREATE TABLE reporting_order (
  id uuid PRIMARY KEY,
  version bigint NOT NULL,
  customer_id uuid NOT NULL,
  source_version bigint NOT NULL CHECK (source_version >= 0),
  status varchar(24) NOT NULL,
  total numeric(16,2) NOT NULL CHECK (total > 0),
  created_at timestamptz NOT NULL,
  created_date date NOT NULL,
  paid_at timestamptz,
  completed_at timestamptz,
  completed_date date,
  cancelled_at timestamptz,
  payment_id uuid,
  refund_status varchar(20) NOT NULL,
  refund_id uuid
);
CREATE INDEX reporting_order_created ON reporting_order (created_date, status);
CREATE INDEX reporting_order_completed ON reporting_order (completed_date, status);
CREATE INDEX reporting_order_status ON reporting_order (status);
CREATE INDEX reporting_order_refund_status ON reporting_order (refund_status);
CREATE TABLE reporting_product (
  id uuid PRIMARY KEY,
  version bigint NOT NULL,
  name varchar(100) NOT NULL,
  kind varchar(20) NOT NULL,
  observed_at timestamptz NOT NULL,
  source_order_id uuid NOT NULL
);
CREATE TABLE reporting_line (
  id uuid PRIMARY KEY,
  order_id uuid NOT NULL REFERENCES reporting_order(id),
  product_id uuid NOT NULL REFERENCES reporting_product(id),
  kind varchar(20) NOT NULL,
  quantity integer NOT NULL CHECK (quantity BETWEEN 1 AND 99),
  subtotal numeric(18,2) NOT NULL CHECK (subtotal > 0)
);
CREATE INDEX reporting_line_order ON reporting_line (order_id);
CREATE INDEX reporting_line_product ON reporting_line (product_id);
CREATE TABLE reporting_customer (
  id uuid PRIMARY KEY,
  version bigint NOT NULL,
  created_at timestamptz NOT NULL,
  created_date date NOT NULL
);
CREATE INDEX reporting_customer_created ON reporting_customer (created_date);
CREATE TABLE reporting_receipt (
  id uuid PRIMARY KEY,
  version bigint NOT NULL,
  order_id uuid NOT NULL,
  amount numeric(16,2) NOT NULL CHECK (amount > 0),
  paid_at timestamptz NOT NULL,
  business_date date NOT NULL
);
CREATE INDEX reporting_receipt_date ON reporting_receipt (business_date);
CREATE INDEX reporting_receipt_order ON reporting_receipt (order_id);
CREATE TABLE reporting_refund (
  id uuid PRIMARY KEY,
  version bigint NOT NULL,
  payment_id uuid NOT NULL UNIQUE,
  order_id uuid NOT NULL,
  amount numeric(16,2) NOT NULL CHECK (amount > 0),
  confirmed_at timestamptz NOT NULL,
  business_date date NOT NULL
);
CREATE INDEX reporting_refund_date ON reporting_refund (business_date);
