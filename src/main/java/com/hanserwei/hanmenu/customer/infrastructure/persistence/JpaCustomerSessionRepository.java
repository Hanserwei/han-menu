package com.hanserwei.hanmenu.customer.infrastructure.persistence;

import com.hanserwei.hanmenu.customer.domain.CustomerAccount;
import com.hanserwei.hanmenu.customer.domain.CustomerSessionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.PredicateSpecification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 顾客会话的 JPA 仓储适配器. */
@Repository
@Transactional
class JpaCustomerSessionRepository implements CustomerSessionRepository {
  private final CustomerSessionRecords sessions;
  private final CustomerRecords customers;

  JpaCustomerSessionRepository(CustomerSessionRecords sessions, CustomerRecords customers) {
    this.sessions = sessions;
    this.customers = customers;
  }

  @Override
  public void add(String tokenHash, UUID customerId, long securityVersion, Instant expiresAt) {
    sessions.saveAndFlush(
        new CustomerSessionEntity(
            tokenHash, customers.getReferenceById(customerId), securityVersion, expiresAt));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CustomerAccount> findActive(String tokenHash, Instant now) {
    return sessions
        .findByTokenHashAndExpiresAtAfterAndCustomerEnabledTrue(tokenHash, now)
        .filter(CustomerSessionEntity::matchesSecurityVersion)
        .map(session -> session.customer.domain());
  }

  @Override
  public void revoke(String tokenHash) {
    sessions.deleteById(tokenHash);
  }

  @Override
  public void deleteExpired(Instant now) {
    PredicateSpecification<CustomerSessionEntity> expired =
        (root, builder) -> builder.lessThanOrEqualTo(root.get("expiresAt"), now);
    sessions.delete(expired);
  }
}
