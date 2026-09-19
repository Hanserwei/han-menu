package com.hanserwei.hanmenu.reporting.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** LineFact 的统计表派生查询. */
interface LineFactRecords extends JpaRepository<LineFactEntity, java.util.UUID> {}
