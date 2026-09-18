package com.hanserwei.hanmenu.catalog.infrastructure.persistence;

import com.hanserwei.hanmenu.catalog.domain.CatalogImage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 图片元数据，不保存临时下载 URL 或外部存储凭证. */
@Entity(name = "CatalogImage")
@Table(name = "catalog_image")
public class ImageEntity {
  @Id UUID id;

  @Column(nullable = false, unique = true, length = 200)
  String objectKey;

  @Column(nullable = false, length = 32)
  String mediaType;

  @Column(nullable = false)
  long size;

  @Column(nullable = false)
  Instant createdAt;

  /** ORM 构造入口. */
  protected ImageEntity() {}

  static ImageEntity from(CatalogImage image) {
    var entity = new ImageEntity();
    entity.id = image.id();
    entity.objectKey = image.objectKey();
    entity.mediaType = image.mediaType();
    entity.size = image.size();
    entity.createdAt = image.createdAt();
    return entity;
  }

  CatalogImage domain() {
    return new CatalogImage(id, objectKey, mediaType, size, createdAt);
  }
}
