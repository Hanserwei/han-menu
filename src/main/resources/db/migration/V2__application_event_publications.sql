-- Spring Modulith 2.1 的 JPA 事件登记实体使用该结构，DDL 统一由 Flyway 管理。
CREATE TABLE event_publication (
    id UUID PRIMARY KEY,
    listener_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMPTZ NOT NULL,
    completion_date TIMESTAMPTZ,
    status VARCHAR(255),
    completion_attempts INTEGER NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMPTZ
);
CREATE INDEX event_publication_completion_idx ON event_publication (completion_date);
