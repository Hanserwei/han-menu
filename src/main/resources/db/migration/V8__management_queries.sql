-- 管理端组合检索只增加本模块索引，不修改既有业务数据或跨模块建立外键。
CREATE INDEX ordering_management_history ON ordering_order (created_at DESC, id DESC);
CREATE INDEX ordering_phone_history ON ordering_order (phone, created_at DESC, id DESC);
CREATE INDEX customer_management_history ON customer_account (created_at DESC, id DESC);
CREATE INDEX customer_enabled_history ON customer_account (enabled, created_at DESC, id DESC);
CREATE INDEX payment_management_history ON payment_intent (created_at DESC, id DESC);
CREATE INDEX payment_status_history ON payment_intent (status, created_at DESC, id DESC);
CREATE INDEX payment_customer_history ON payment_intent (customer_id, created_at DESC, id DESC);
CREATE INDEX refund_management_history ON payment_refund (created_at DESC, id DESC);
CREATE INDEX refund_status_history ON payment_refund (status, created_at DESC, id DESC);
CREATE INDEX refund_customer_history ON payment_refund (customer_id, created_at DESC, id DESC);
CREATE INDEX refund_business_history ON payment_refund (business_ref, created_at DESC, id DESC);
CREATE INDEX identity_audit_history ON identity_audit (occurred_at DESC, id DESC);
CREATE INDEX identity_audit_action_history ON identity_audit (action, occurred_at DESC, id DESC);
CREATE INDEX identity_audit_actor_history ON identity_audit (actor_id, occurred_at DESC, id DESC);
CREATE INDEX identity_audit_subject_history ON identity_audit (subject_id, occurred_at DESC, id DESC);
