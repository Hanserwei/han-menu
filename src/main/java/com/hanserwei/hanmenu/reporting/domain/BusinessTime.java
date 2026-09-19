package com.hanserwei.hanmenu.reporting.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** 单店经营日期统一按上海时区，源事件与数据库快照统一至 PostgreSQL 微秒精度. */
public final class BusinessTime {
  /** 当前单店经营时区，不跟随服务器默认时区改变口径. */
  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  private BusinessTime() {}

  /** 对齐 PostgreSQL 时间舍入，避免事件原始纳秒与重建快照产生假冲突. */
  public static Instant canonical(Instant value) {
    return value == null
        ? null
        : Instant.ofEpochSecond(value.getEpochSecond(), ((value.getNano() + 500L) / 1000L) * 1000L);
  }

  /** 将 UTC 事实时间映射为固定经营日期. */
  public static LocalDate date(Instant value) {
    return value == null ? null : canonical(value).atZone(ZONE).toLocalDate();
  }
}
