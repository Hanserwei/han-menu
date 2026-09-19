package com.hanserwei.hanmenu.identity.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 身份模块的安全审计只读查询端口，不表示全系统业务操作日志. */
public interface AuditQuery {
  /** 根据固定事件类型、内部主体标识、结果及时间执行有界检索. */
  Page search(Filter filter);

  /** 可组合审计条件，发生时刻采用左闭右开区间. */
  record Filter(
      AuditTrail.Action action,
      UUID actorId,
      UUID subjectId,
      Boolean successful,
      Instant from,
      Instant to,
      int page,
      int size) {
    /** 验证分页和时间先后，防止无界查询. */
    public Filter {
      if (page < 0
          || page > 10000
          || size < 1
          || size > 50
          || (from != null && to != null && !from.isBefore(to))) {
        throw new IdentityException(IdentityException.Reason.INVALID_INPUT, "分页或时间范围不合法");
      }
    }
  }

  /** 审计事实不包含自由文本、请求体、凭证或个人资料. */
  record Entry(
      UUID id,
      AuditTrail.Action action,
      UUID actorId,
      UUID subjectId,
      boolean successful,
      Instant occurredAt) {}

  /** 审计查询结果及匹配总量. */
  record Page(List<Entry> items, long totalElements) {
    /** 固定分页集合. */
    public Page {
      items = List.copyOf(items);
    }
  }
}
