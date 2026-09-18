package com.hanserwei.hanmenu.catalog.domain;

import java.net.URI;

/** 私有对象存储端口，上传路径由服务端决定，业务不接触渠道密钥. */
public interface ImageStorage {
  /** 上传已验证图片，完成后对象可读. */
  void put(String key, byte[] bytes, String mediaType);

  /** 删除本次上传的孤立对象，用于数据库失败时的补偿. */
  void delete(String key);

  /** 返回短期可读签名地址，URL 不进入缓存或数据库. */
  URI downloadUrl(String key);
}
