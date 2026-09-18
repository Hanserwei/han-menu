package com.hanserwei.hanmenu.customer.application;

import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import com.hanserwei.hanmenu.customer.domain.AddressRepository;
import com.hanserwei.hanmenu.customer.domain.CustomerException;
import com.hanserwei.hanmenu.customer.domain.DeliveryAddress;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 地址簿用例，所有仓储操作带顾客 ID，防止水平越权. */
@Service
@Transactional
public class AddressBookService {
  private final AddressRepository addresses;
  private final CustomerAuthentication authentication;
  private final Clock clock;

  /** 组合地址仓储和顾客身份校验. */
  public AddressBookService(
      AddressRepository addresses, CustomerAuthentication authentication, Clock clock) {
    this.addresses = addresses;
    this.authentication = authentication;
    this.clock = clock;
  }

  /** 返回当前顾客自己的地址列表. */
  @Transactional(readOnly = true)
  public List<DeliveryAddress> list(CustomerIdentity identity) {
    authentication.current(identity);
    return addresses.findByCustomer(identity.customerId());
  }

  /** 创建地址，默认状态由请求明确决定；默认切换在同一事务执行. */
  public DeliveryAddress create(CustomerIdentity identity, AddressCommand command) {
    authentication.current(identity);
    addresses.lockOwner(identity.customerId());
    if (addresses.findByCustomer(identity.customerId()).size() >= 20) {
      throw new CustomerException(CustomerException.Reason.CONFLICT, "地址簿最多 20 条地址");
    }
    var address = command.toDomain(identity.customerId(), clock.instant());
    if (command.defaultAddress()) {
      addresses.clearDefault(identity.customerId(), address.id());
      address.makeDefault(clock.instant());
    }
    addresses.add(address);
    return address;
  }

  /** 修改自己的地址，版本必填. */
  public DeliveryAddress update(
      CustomerIdentity identity, UUID id, AddressCommand command, long version) {
    authentication.current(identity);
    addresses.lockOwner(identity.customerId());
    var address = own(identity.customerId(), id);
    address.requireVersion(version);
    address.revise(
        command.label(),
        command.recipientName(),
        command.phone(),
        command.province(),
        command.city(),
        command.district(),
        command.detail(),
        clock.instant());
    if (command.defaultAddress()) {
      addresses.clearDefault(identity.customerId(), address.id());
      address.makeDefault(clock.instant());
    }
    if (!command.defaultAddress()) {
      address.clearDefault(clock.instant());
    }
    addresses.update(address);
    return own(identity.customerId(), id);
  }

  /** 显式切换默认地址，数据库唯一索引作为最终兜底. */
  public DeliveryAddress makeDefault(CustomerIdentity identity, UUID id, long version) {
    authentication.current(identity);
    addresses.lockOwner(identity.customerId());
    var address = own(identity.customerId(), id);
    address.requireVersion(version);
    addresses.clearDefault(identity.customerId(), address.id());
    address.makeDefault(clock.instant());
    addresses.update(address);
    return own(identity.customerId(), id);
  }

  /** 删除自己的地址；删除默认地址后不自动选择其他地址. */
  public void delete(CustomerIdentity identity, UUID id, long version) {
    authentication.current(identity);
    addresses.lockOwner(identity.customerId());
    var address = own(identity.customerId(), id);
    address.requireVersion(version);
    addresses.delete(address);
  }

  /** 读取所属顾客的单一地址，未知和他人地址均返回不存在. */
  @Transactional(readOnly = true)
  public DeliveryAddress get(CustomerIdentity identity, UUID id) {
    authentication.current(identity);
    return own(identity.customerId(), id);
  }

  private DeliveryAddress own(UUID customerId, UUID id) {
    return addresses
        .findByCustomerAndId(customerId, id)
        .orElseThrow(() -> new CustomerException(CustomerException.Reason.NOT_FOUND, "地址不存在"));
  }

  /** 地址请求对象，数据库和 HTTP 层都不直接接受实体. */
  public record AddressCommand(
      String label,
      String recipientName,
      String phone,
      String province,
      String city,
      String district,
      String detail,
      boolean defaultAddress) {
    @Override
    public String toString() {
      return "AddressCommand[收货资料已隐藏]";
    }

    /** 创建领域地址并使用服务端当前时刻. */
    public DeliveryAddress toDomain(UUID customerId, java.time.Instant now) {
      var address =
          new DeliveryAddress(
              UUID.randomUUID(),
              customerId,
              label,
              recipientName,
              phone,
              province,
              city,
              district,
              detail,
              false,
              0,
              now,
              now);
      if (defaultAddress) {
        address.makeDefault(now);
      }
      return address;
    }
  }
}
