package com.hanserwei.hanmenu.catalog.infrastructure;

import com.hanserwei.hanmenu.catalog.domain.CatalogCache;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 公开目录缓存适配器，短 TTL 限制旧代际占用，任何 Redis 故障都回源而不影响目录事实. */
@Component
class RedisCatalogCache implements CatalogCache {
  private static final Logger LOGGER = LoggerFactory.getLogger(RedisCatalogCache.class);
  private final StringRedisTemplate redis;
  private final String prefix;

  RedisCatalogCache(
      StringRedisTemplate redis, @Value("${han-menu.catalog.cache-prefix}") String prefix) {
    if (!prefix.startsWith("han-menu:")) {
      throw new IllegalArgumentException("缓存前缀不合法");
    }
    this.redis = redis;
    this.prefix = prefix;
  }

  @Override
  public Optional<String> get(String key) {
    try {
      return Optional.ofNullable(redis.opsForValue().get(prefix + key));
    } catch (DataAccessException ignored) {
      return Optional.empty();
    }
  }

  @Override
  public void put(String key, String value) {
    try {
      redis.opsForValue().set(prefix + key, value, Duration.ofMinutes(2));
    } catch (DataAccessException ignored) {
      // 目录数据库仍是事实来源，缓存失败不使读取失败。
      LOGGER.debug("catalog_cache_write_unavailable");
    }
  }
}
