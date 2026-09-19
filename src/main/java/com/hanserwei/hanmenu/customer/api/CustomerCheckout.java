package com.hanserwei.hanmenu.customer.api;

import java.util.UUID;

/** 订单结算所需的顾客与地址契约，不暴露顾客领域类型. */
public interface CustomerCheckout {
  /** 在调用方事务内锁定顾客并重新校验身份，串行处理本人地址变更与订单幂等请求. */
  void lockActive(CustomerIdentity identity);

  /** 读取本人的指定版本地址；他人地址与不存在地址均拒绝. */
  AddressSnapshot address(CustomerIdentity identity, UUID addressId, long version);

  /** 交付给订单的不可变收货资料，字符串表示隐藏个人信息. */
  record AddressSnapshot(
      UUID id,
      long version,
      String recipientName,
      String phone,
      String province,
      String city,
      String district,
      String detail) {
    @Override
    public String toString() {
      return "AddressSnapshot[收货资料已隐藏]";
    }
  }
}
