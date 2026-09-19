package com.hanserwei.hanmenu.customer.application;

import com.hanserwei.hanmenu.customer.api.CustomerFacts;
import com.hanserwei.hanmenu.customer.domain.CustomerRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 为投影重建导出最小注册数据，读取不依赖会话或缓存. */
@Service
@Transactional(readOnly = true)
class CustomerFactsService implements CustomerFacts {
  private final CustomerRepository customers;

  CustomerFactsService(CustomerRepository customers) {
    this.customers = customers;
  }

  @Override
  public List<Registration> after(UUID cursor, int limit) {
    if (limit < 1 || limit > 200) {
      throw new IllegalArgumentException("统计批次须为 1 至 200");
    }
    return customers.factsAfter(cursor, limit).stream()
        .map(value -> new Registration(value.id(), value.createdAt()))
        .toList();
  }
}
