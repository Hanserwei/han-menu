package com.hanserwei.hanmenu.identity.api;

import java.util.Optional;
import java.util.UUID;

/** 向长连接提供可撤销的员工会话证明，不暴露账号实体或原始令牌. */
public interface StaffSessions {
  /** 校验原始员工 Bearer 并返回仅供模块内部保存的会话证明. */
  Optional<Proof> authenticate(String accessToken);

  /** 每次敏感发送前重验原会话，退出、到期、禁用和改密都能撤销长连接. */
  boolean active(Proof proof);

  /** 会话摘要是内部凭证，不能写入 URL、日志或公开响应. */
  record Proof(UUID employeeId, long securityVersion, String sessionHash) {
    @Override
    public String toString() {
      return "StaffSessionProof[employeeId=" + employeeId + ", 凭证已隐藏]";
    }
  }
}
