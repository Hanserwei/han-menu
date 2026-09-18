package com.hanserwei.hanmenu.shop.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** 单店配置的 Spring Data 仓储. */
interface ShopRecords extends JpaRepository<ShopEntity, Integer> {}
