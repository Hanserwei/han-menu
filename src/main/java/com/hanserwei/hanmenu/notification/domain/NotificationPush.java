package com.hanserwei.hanmenu.notification.domain;

/** 在线提示投递端口；适配器必须在每次发送前重验会话，网络写入不能占用数据库事务. */
public interface NotificationPush {
  /** 向所有当前合法连接广播，返回成功与失败数量；消息可能重复，客户端按游标去重. */
  Outcome send(Notice notice);

  /** 清理退出、到期或账号被撤销的空闲连接. */
  void prune();

  /** 没有在线订阅者不等于投递成功. */
  record Outcome(int sent, int failed) {
    /** 全部当前合法连接发送成功才完成本次实时提示任务. */
    public boolean successful() {
      return sent > 0 && failed == 0;
    }

    /** 返回不包含网络细节或身份的固定失败分类. */
    public String failure() {
      return failed > 0 ? "SEND_FAILED" : "NO_SUBSCRIBERS";
    }
  }
}
