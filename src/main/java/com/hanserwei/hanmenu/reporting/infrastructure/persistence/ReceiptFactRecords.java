package com.hanserwei.hanmenu.reporting.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** ReceiptFact 的统计表派生查询. */
interface ReceiptFactRecords extends JpaRepository<ReceiptFactEntity, java.util.UUID> {}
