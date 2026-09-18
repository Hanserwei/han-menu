package com.hanserwei.hanmenu.catalog.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 目录写入的事务串行点与缓存代际，适合单店后台低频管理写入. */
@Entity(name = "CatalogRevision")
@Table(name = "catalog_revision")
public class RevisionEntity {
  @Id Integer id;
  long revision;

  /** ORM 构造入口. */
  protected RevisionEntity() {}
}
