package com.hanserwei.hanmenu.identity.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 由 Spring Data 负责审计记录持久化，不向其他模块公开查询业务表的入口. */
interface AuditRecords
    extends JpaRepository<AuditEntity, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<AuditEntity> {}
