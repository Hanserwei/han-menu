package com.hanserwei.hanmenu.reporting.domain;

import java.time.Instant;

/** 投影控制聚合串行化事件更新和原子重建，保留可见版本与更新时间. */
public final class ProjectionState {
  private final long version;
  private long generation;
  private long revision;
  private boolean initialized;
  private Instant updatedAt;
  private Instant rebuiltAt;

  /** 重建控制状态，不改变既有投影代际. */
  public ProjectionState(
      long version,
      long generation,
      long revision,
      boolean initialized,
      Instant updatedAt,
      Instant rebuiltAt) {
    this.version = version;
    this.generation = generation;
    this.revision = revision;
    this.initialized = initialized;
    this.updatedAt = updatedAt;
    this.rebuiltAt = rebuiltAt;
  }

  /** 只有已完成基线重建的投影可以回答业务报表. */
  public void requireReady() {
    if (!initialized) {
      throw new ReportingException(ReportingException.Reason.NOT_READY, "统计投影尚未初始化，请重建后查询");
    }
  }

  /** 管理员维护操作必须基于当前控制版本. */
  public void requireVersion(long expected) {
    if (version != expected) {
      throw new ReportingException(ReportingException.Reason.VERSION_CONFLICT, "统计投影已更新");
    }
  }

  /** 接受真实新事实才推进修订号，重复投递不制造新的数据版本. */
  public void applied(Instant now) {
    revision = Math.incrementExact(revision);
    updatedAt = now;
  }

  /** 原子重建成功后切换代际；失败时由同一数据库事务全部回滚. */
  public void rebuilt(Instant now) {
    applied(now);
    generation = Math.incrementExact(generation);
    initialized = true;
    rebuiltAt = now;
  }

  /** 返回控制版本. */
  public long version() {
    return version;
  }

  /** 返回重建代际. */
  public long generation() {
    return generation;
  }

  /** 返回统计修订号. */
  public long revision() {
    return revision;
  }

  /** 返回是否已完成完整基线. */
  public boolean initialized() {
    return initialized;
  }

  /** 返回最后接受事实的时间. */
  public Instant updatedAt() {
    return updatedAt;
  }

  /** 返回上次重建成功时间. */
  public Instant rebuiltAt() {
    return rebuiltAt;
  }
}
