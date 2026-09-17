-- 全新应用的身份模块基线，仅定义当前业务模型，不导入旧项目用户和数据。
CREATE TABLE identity_employee (
    id UUID PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE,
    display_name VARCHAR(50) NOT NULL,
    phone VARCHAR(16) NOT NULL DEFAULT '',
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN', 'STAFF')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    security_version BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (role <> 'ADMIN' OR enabled)
);
CREATE UNIQUE INDEX identity_single_administrator_idx ON identity_employee (role)
    WHERE role = 'ADMIN';

CREATE TABLE identity_session (
    token_hash VARCHAR(64) PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES identity_employee (id),
    security_version BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX identity_session_employee_idx ON identity_session (employee_id);
CREATE INDEX identity_session_expiry_idx ON identity_session (expires_at);

CREATE TABLE identity_audit (
    id UUID PRIMARY KEY,
    action VARCHAR(40) NOT NULL,
    successful BOOLEAN NOT NULL,
    actor_id UUID,
    subject_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX identity_audit_time_idx ON identity_audit (occurred_at);
