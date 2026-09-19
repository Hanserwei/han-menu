package com.hanserwei.hanmenu.reporting.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** ProductFact 的统计表派生查询. */
interface ProductFactRecords extends JpaRepository<ProductFactEntity, java.util.UUID> {}
