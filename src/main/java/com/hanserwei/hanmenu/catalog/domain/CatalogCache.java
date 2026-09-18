package com.hanserwei.hanmenu.catalog.domain;

import java.util.Optional;

/** 缓存只持有公开目录序列化快照，失败时回源数据库，不作为业务真相来源. */
public interface CatalogCache {
  /** 查询带修订号的缓存键. */
  Optional<String> get(String key);

  /** 写入有限期快照；提交后新修订号天然使旧缓存不可达. */
  void put(String key, String value);
}
