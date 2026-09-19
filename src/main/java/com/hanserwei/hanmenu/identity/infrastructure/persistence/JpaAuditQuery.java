package com.hanserwei.hanmenu.identity.infrastructure.persistence;

import com.hanserwei.hanmenu.identity.domain.AuditQuery;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 使用本模块 JPA Specification 检索审计事实，排序和总量统计均在数据库执行. */
@Repository
@Transactional(readOnly = true)
class JpaAuditQuery implements AuditQuery {
  private final AuditRecords records;

  JpaAuditQuery(AuditRecords records) {
    this.records = records;
  }

  @Override
  public Page search(Filter filter) {
    Specification<AuditEntity> spec =
        (root, query, builder) -> {
          var predicates = new ArrayList<Predicate>();
          if (filter.action() != null) {
            predicates.add(builder.equal(root.get("action"), filter.action()));
          }
          if (filter.actorId() != null) {
            predicates.add(builder.equal(root.get("actorId"), filter.actorId()));
          }
          if (filter.subjectId() != null) {
            predicates.add(builder.equal(root.get("subjectId"), filter.subjectId()));
          }
          if (filter.successful() != null) {
            predicates.add(builder.equal(root.get("successful"), filter.successful()));
          }
          if (filter.from() != null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), filter.from()));
          }
          if (filter.to() != null) {
            predicates.add(builder.lessThan(root.get("occurredAt"), filter.to()));
          }
          return builder.and(predicates.toArray(Predicate[]::new));
        };
    var page =
        records.findAll(
            spec,
            PageRequest.of(
                filter.page(),
                filter.size(),
                Sort.by("occurredAt").descending().and(Sort.by("id").descending())));
    return new Page(page.stream().map(AuditEntity::entry).toList(), page.getTotalElements());
  }
}
