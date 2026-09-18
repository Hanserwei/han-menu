package com.hanserwei.hanmenu.cart.infrastructure.persistence;

import com.hanserwei.hanmenu.cart.domain.CartRepository;
import com.hanserwei.hanmenu.cart.domain.ShoppingCart;
import java.util.Optional;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 购物车 JPA 仓储，使用实体版本阻止并发数量更新互相覆盖. */
@Repository
@Transactional
class JpaCartRepository implements CartRepository {
  private final CartRecords records;

  JpaCartRepository(CartRecords records) {
    this.records = records;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ShoppingCart> findByCustomer(UUID customerId) {
    return records.findById(customerId).map(CartEntity::domain);
  }

  @Override
  public void add(ShoppingCart cart) {
    // 空资源版本为零；首次实际变更持久化为一，网络重试的旧版本不能再次增加数量。
    var entity =
        records.saveAndFlush(
            CartEntity.create(ShoppingCart.empty(cart.customerId(), java.time.Instant.EPOCH)));
    entity.apply(cart);
    records.flush();
  }

  @Override
  public void update(ShoppingCart cart) {
    var entity =
        records
            .findById(cart.customerId())
            .orElseThrow(
                () ->
                    new ObjectOptimisticLockingFailureException(
                        CartEntity.class, cart.customerId()));
    if (entity.version != cart.version()) {
      throw new ObjectOptimisticLockingFailureException(CartEntity.class, cart.customerId());
    }
    entity.apply(cart);
    records.flush();
  }
}
