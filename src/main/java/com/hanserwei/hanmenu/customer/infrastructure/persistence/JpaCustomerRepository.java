package com.hanserwei.hanmenu.customer.infrastructure.persistence;

import com.hanserwei.hanmenu.customer.domain.CustomerAccount;
import com.hanserwei.hanmenu.customer.domain.CustomerException;
import com.hanserwei.hanmenu.customer.domain.CustomerPage;
import com.hanserwei.hanmenu.customer.domain.CustomerRepository;
import com.hanserwei.hanmenu.customer.domain.CustomerSearch;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 顾客聚合 JPA 仓储适配器. */
@Repository
@Transactional
class JpaCustomerRepository implements CustomerRepository {
  private final CustomerRecords records;

  JpaCustomerRepository(CustomerRecords records) {
    this.records = records;
  }

  @Override
  public void add(CustomerAccount customer) {
    records.saveAndFlush(CustomerEntity.create(customer));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CustomerAccount> findById(UUID id) {
    return records.findById(id).map(CustomerEntity::domain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CustomerAccount> findByPhone(String phone) {
    return records.findByPhone(phone).map(CustomerEntity::domain);
  }

  @Override
  public void update(CustomerAccount customer) {
    var entity =
        records
            .findById(customer.id())
            .orElseThrow(
                () ->
                    new ObjectOptimisticLockingFailureException(
                        CustomerEntity.class, customer.id()));
    if (entity.version != customer.version()) {
      throw new ObjectOptimisticLockingFailureException(CustomerEntity.class, customer.id());
    }
    entity.apply(customer);
    records.flush();
  }

  @Override
  public CustomerAccount lock(UUID id) {
    return records
        .findLockedById(id)
        .orElseThrow(() -> new CustomerException(CustomerException.Reason.NOT_FOUND, "顾客不存在"))
        .domain();
  }

  @Override
  @Transactional(readOnly = true)
  public CustomerPage search(CustomerSearch search) {
    Specification<CustomerEntity> filters =
        (root, query, builder) -> {
          var predicates = new ArrayList<Predicate>();
          if (search.phone() != null) {
            predicates.add(builder.equal(root.get("phone"), search.phone()));
          }
          if (search.name() != null) {
            String literal = search.name().replace("!", "!!").replace("%", "!%").replace("_", "!_");
            predicates.add(builder.like(root.get("displayName"), "%" + literal + "%", '!'));
          }
          if (search.enabled() != null) {
            predicates.add(builder.equal(root.get("enabled"), search.enabled()));
          }
          if (search.from() != null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), search.from()));
          }
          if (search.to() != null) {
            predicates.add(builder.lessThan(root.get("createdAt"), search.to()));
          }
          return builder.and(predicates.toArray(Predicate[]::new));
        };
    var page =
        records.findAll(
            filters,
            PageRequest.of(
                search.page(),
                search.size(),
                Sort.by("createdAt").descending().and(Sort.by("id").descending())));
    return new CustomerPage(
        page.stream().map(CustomerEntity::domain).toList(), page.getTotalElements());
  }

  @Override
  @Transactional(readOnly = true)
  public List<CustomerAccount> factsAfter(UUID cursor, int limit) {
    var page = PageRequest.of(0, limit);
    return (cursor == null
            ? records.findAllByOrderByIdAsc(page)
            : records.findByIdGreaterThanOrderByIdAsc(cursor, page))
        .stream().map(CustomerEntity::domain).toList();
  }
}
