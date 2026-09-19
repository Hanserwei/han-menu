package com.hanserwei.hanmenu.notification.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Receipt 的派生查询与必要事务锁. */
interface ReceiptRecords extends JpaRepository<ReceiptEntity, UUID> {}
