package com.hanserwei.hanmenu.customer.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 顾客增长统计的最小公开快照契约. */
public interface CustomerFacts {
  /** 按 UUID 键集分页读取注册事实，每批最多二百条. */
  List<Registration> after(UUID cursor, int limit);

  /** 注册统计不导出个人资料或认证数据. */
  record Registration(UUID id, Instant createdAt) {}
}
