package com.hanserwei.hanmenu.identity.web;

/** 旧后台的响应信封，code 为 1 表示成功，为 0 表示失败，状态语义同时体现在 HTTP 中. */
public record LegacyResponse<T>(int code, String msg, T data) {
  /** 创建成功响应，保留旧客户端读取 data 的约定. */
  public static <T> LegacyResponse<T> success(T data) {
    return new LegacyResponse<>(1, null, data);
  }

  /** 创建失败响应，不在 data 中返回异常对象或请求信息. */
  public static LegacyResponse<Void> failure(String message) {
    return new LegacyResponse<>(0, message, null);
  }
}
