package com.hanserwei.hanmenu.customer.api;

/** 向购物车等模块提供顾客身份校验，防止只信任调用方构造的身份快照. */
public interface CustomerAuthorization {
  /** 检查当前账号和安全版本；失败时拒绝操作. */
  void requireActive(CustomerIdentity identity);
}
