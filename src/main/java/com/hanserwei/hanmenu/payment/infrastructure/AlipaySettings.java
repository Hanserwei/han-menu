package com.hanserwei.hanmenu.payment.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 支付宝沙箱凭证只从环境装配，调试字符串不得输出密钥. */
@ConfigurationProperties("han-menu.payment.alipay")
public record AlipaySettings(
    String appId,
    String sellerId,
    String privateKey,
    String publicKey,
    String gateway,
    String notifyUrl) {
  @Override
  public String toString() {
    return "AlipaySettings[沙箱凭证已隐藏]";
  }
}
