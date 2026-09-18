package com.hanserwei.hanmenu.shop.domain;

/** 单店聚合，营业状态属于数据库事实，资料不完整时不能开店. */
public final class Shop {
  private String name;
  private String phone;
  private String address;
  private boolean open;
  private final long version;

  /** 从存储快照重建，默认关闭状态允许尚未填写电话和地址. */
  public Shop(String name, String phone, String address, boolean open, long version) {
    if (version < 0) {
      throw new IllegalArgumentException("版本不可为负");
    }
    this.version = version;
    revise(name, phone, address);
    changeOpen(open);
  }

  /** 修改店铺资料，营业中的门店必须继续满足完整性规则. */
  public void revise(String name, String phone, String address) {
    if (name == null
        || name.isBlank()
        || name.strip().length() > 100
        || phone == null
        || !phone.matches("(?:\\+?[1-9][0-9]{6,14})?")
        || address == null
        || address.strip().length() > 300) {
      throw new ShopException(ShopException.Reason.INVALID_INPUT, "店铺资料不合法");
    }
    if (open && (phone.isBlank() || address.isBlank())) {
      throw new ShopException(ShopException.Reason.CONFLICT, "营业中的店铺必须保留电话和地址");
    }
    this.name = name.strip();
    this.phone = phone;
    this.address = address.strip();
  }

  /** 只有资料完整的门店才能进入营业状态. */
  public void changeOpen(boolean value) {
    if (value && (phone.isBlank() || address.isBlank())) {
      throw new ShopException(ShopException.Reason.CONFLICT, "请先配置联系电话和地址再营业");
    }
    open = value;
  }

  /** 对所有管理修改执行版本校验. */
  public void requireVersion(long expected) {
    if (version != expected) {
      throw new ShopException(ShopException.Reason.VERSION_CONFLICT, "店铺配置已更新");
    }
  }

  /** 返回名称. */
  public String name() {
    return name;
  }

  /** 返回联系电话. */
  public String phone() {
    return phone;
  }

  /** 返回地址. */
  public String address() {
    return address;
  }

  /** 返回是否营业. */
  public boolean open() {
    return open;
  }

  /** 返回持久化版本. */
  public long version() {
    return version;
  }
}
