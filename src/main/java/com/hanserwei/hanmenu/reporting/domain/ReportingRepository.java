package com.hanserwei.hanmenu.reporting.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 统计仓储仅操作本模块投影表，源业务数据必须通过公开快照契约获取. */
public interface ReportingRepository {
  /** 读取当前可见投影元信息. */
  ProjectionState state();

  /** 在事件应用或重建事务中锁定控制行. */
  ProjectionState lock();

  /** 按版本保存投影状态. */
  void saveState(ProjectionState state);

  /** 按源版本幂等应用订单，返回是否接受新事实. */
  boolean order(ReportingFacts.Order value);

  /** 幂等登记最小顾客注册事实. */
  boolean customer(ReportingFacts.Customer value);

  /** 幂等登记真实收款事实. */
  boolean receipt(ReportingFacts.Receipt value);

  /** 幂等登记真实退款事实. */
  boolean refund(ReportingFacts.Refund value);

  /** 清空当前事务内的投影，失败时旧投影仍能回滚恢复. */
  void clear();

  /** 结束一个重建批次，刷新并释放 ORM 快照，限制内存占用. */
  void finishBatch();

  /** 聚合有界经营日期内的订单、营业额、资金及注册日账. */
  ReportData.Aggregates aggregate(ReportPeriod period);

  /** 按数量、金额及商品标识稳定排序返回销量排行. */
  List<ReportData.Sale> sales(ReportPeriod period, int limit);

  /** 在本模块投影内核对订单、支付及退款引用和金额. */
  ReportData.Reconciliation reconcile(ReportPeriod period);

  /** 返回当前各订单状态数量，不用过去现金流推导待处理订单. */
  Map<String, Long> statuses();

  /** 返回业务日创建和完成数量，供普通员工工作台使用. */
  Map<String, Long> today(LocalDate date);

  /** 返回投影记录数量供维护确认. */
  Counts counts();

  /** 维护元信息中的四类源事实数量. */
  record Counts(long orders, long customers, long receipts, long refunds) {}
}
