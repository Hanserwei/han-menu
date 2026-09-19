package com.hanserwei.hanmenu.reporting.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** RefundFact 的统计表派生查询. */
interface RefundFactRecords extends JpaRepository<RefundFactEntity, java.util.UUID> {}
