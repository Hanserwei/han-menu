package com.hanserwei.hanmenu.cart.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** 购物车查询一次抓取条目集合，避免 Open-in-View 下的懒加载. */
interface CartRecords extends JpaRepository<CartEntity, UUID> {
  @Override
  @EntityGraph(attributePaths = "items")
  Optional<CartEntity> findById(UUID customerId);
}
