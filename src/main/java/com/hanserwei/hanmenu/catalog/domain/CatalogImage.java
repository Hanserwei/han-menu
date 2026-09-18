package com.hanserwei.hanmenu.catalog.domain;

import java.time.Instant;
import java.util.UUID;

/** 图片元数据仅保存受控对象键和媒体类型，不持久化临时签名 URL. */
public record CatalogImage(
    UUID id, String objectKey, String mediaType, long size, Instant createdAt) {}
