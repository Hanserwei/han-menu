package com.hanserwei.hanmenu.shop.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** 单店配置的 Spring Data 仓储. */
interface ShopRecords extends JpaRepository<ShopEntity, Integer> {
  @Lock(LockModeType.PESSIMISTIC_READ)
  Optional<ShopEntity> findLockedById(Integer id);
}
