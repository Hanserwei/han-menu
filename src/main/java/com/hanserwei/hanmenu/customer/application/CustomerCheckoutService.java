package com.hanserwei.hanmenu.customer.application;

import com.hanserwei.hanmenu.customer.api.CustomerCheckout;
import com.hanserwei.hanmenu.customer.api.CustomerIdentity;
import com.hanserwei.hanmenu.customer.domain.AddressRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 将顾客锁及地址版本验证加入订单事务，避免复制过时或不属于本人的地址. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
class CustomerCheckoutService implements CustomerCheckout {
  private final AddressRepository addresses;
  private final CustomerAuthentication authentication;
  private final AddressBookService book;

  CustomerCheckoutService(
      AddressRepository addresses, CustomerAuthentication authentication, AddressBookService book) {
    this.addresses = addresses;
    this.authentication = authentication;
    this.book = book;
  }

  @Override
  public void lockActive(CustomerIdentity identity) {
    addresses.lockOwner(identity.customerId());
    authentication.current(identity);
  }

  @Override
  public AddressSnapshot address(CustomerIdentity identity, UUID addressId, long version) {
    var address = book.get(identity, addressId);
    address.requireVersion(version);
    return new AddressSnapshot(
        address.id(),
        address.version(),
        address.recipientName(),
        address.phone(),
        address.province(),
        address.city(),
        address.district(),
        address.detail());
  }
}
