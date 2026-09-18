package com.hanserwei.hanmenu.catalog.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 图片元数据存取端口，仅模块持久化适配器使用. */
interface ImageRecords extends JpaRepository<ImageEntity, UUID> {}
