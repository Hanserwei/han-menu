package com.hanserwei.hanmenu.shop.infrastructure.persistence;

import com.hanserwei.hanmenu.shop.domain.Shop;
import com.hanserwei.hanmenu.shop.domain.ShopRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 门店 ORM 适配器，仅在应用定义的事务中读写. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaShopRepository implements ShopRepository {
  private final ShopRecords records;

  JpaShopRepository(ShopRecords records) {
    this.records = records;
  }

  @Override
  public Shop get() {
    return records.findById(1).orElseThrow().domain();
  }

  @Override
  public Shop lock() {
    return records.findLockedById(1).orElseThrow().domain();
  }

  @Override
  public void save(Shop value) {
    var entity = records.findById(1).orElseThrow();
    if (entity.version != value.version()) {
      throw new ObjectOptimisticLockingFailureException(ShopEntity.class, 1);
    }
    entity.apply(value);
    records.flush();
  }
}
