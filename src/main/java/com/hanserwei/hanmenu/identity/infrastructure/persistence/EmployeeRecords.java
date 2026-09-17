package com.hanserwei.hanmenu.identity.infrastructure.persistence;

import com.hanserwei.hanmenu.identity.domain.EmployeeAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data 根据方法名生成查询，分页、参数绑定和字面通配符转义交给框架. */
interface EmployeeRecords extends JpaRepository<EmployeeEntity, UUID> {
  boolean existsByRole(EmployeeAccount.Role role);

  Optional<EmployeeEntity> findByUsername(String username);

  Page<EmployeeEntity> findByDisplayNameContainingIgnoreCase(String name, Pageable pageable);
}
