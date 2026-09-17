package com.hanserwei.hanmenu.identity.infrastructure;

import com.hanserwei.hanmenu.identity.domain.EmployeeAccount;
import com.hanserwei.hanmenu.identity.domain.EmployeeProfile;
import com.hanserwei.hanmenu.identity.domain.EmployeeRepository;
import com.hanserwei.hanmenu.identity.domain.IdentityException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 员工仓储的 JDBC 适配器，显式映射聚合并执行乐观锁更新. */
@Repository
class JdbcEmployeeRepository implements EmployeeRepository {
  private final JdbcClient jdbc;

  JdbcEmployeeRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public long nextIdentity() {
    return jdbc.sql("SELECT nextval(pg_get_serial_sequence('identity_employee', 'id'))")
        .query(Long.class)
        .single();
  }

  @Override
  public boolean hasAdministrator() {
    return jdbc.sql("SELECT EXISTS(SELECT 1 FROM identity_employee WHERE role = 'ADMIN')")
        .query(Boolean.class)
        .single();
  }

  @Override
  public void add(EmployeeAccount account) {
    jdbc.sql(
            """
            INSERT INTO identity_employee
              (id, username, name, phone, sex, id_number, password_hash, role,
               enabled, security_version, version, created_at, updated_at)
            VALUES (:id, :username, :name, :phone, :sex, :idNumber, :hash, :role,
                    :enabled, :securityVersion, :version, :createdAt, :updatedAt)
            """)
        .param("id", account.id())
        .param("username", account.profile().username())
        .param("name", account.profile().name())
        .param("phone", account.profile().phone())
        .param("sex", account.profile().sex())
        .param("idNumber", account.profile().idNumber())
        .param("hash", account.passwordHash())
        .param("role", account.role().name())
        .param("enabled", account.enabled())
        .param("securityVersion", account.securityVersion())
        .param("version", account.version())
        .param("createdAt", Timestamp.from(account.createdAt()))
        .param("updatedAt", Timestamp.from(account.updatedAt()))
        .update();
  }

  @Override
  public Optional<EmployeeAccount> findById(long id) {
    return jdbc.sql("SELECT * FROM identity_employee WHERE id = ?")
        .param(id)
        .query(JdbcEmployeeRepository::map)
        .optional();
  }

  @Override
  public Optional<EmployeeAccount> findByUsername(String username) {
    return jdbc.sql("SELECT * FROM identity_employee WHERE username = ?")
        .param(username)
        .query(JdbcEmployeeRepository::map)
        .optional();
  }

  @Override
  public void update(EmployeeAccount account) {
    int rows =
        jdbc.sql(
                """
                UPDATE identity_employee SET username = :username, name = :name, phone = :phone,
                  sex = :sex, id_number = :idNumber, password_hash = :hash, enabled = :enabled,
                  security_version = :securityVersion, updated_at = :updatedAt, version = version + 1
                WHERE id = :id AND version = :version
                """)
            .param("username", account.profile().username())
            .param("name", account.profile().name())
            .param("phone", account.profile().phone())
            .param("sex", account.profile().sex())
            .param("idNumber", account.profile().idNumber())
            .param("hash", account.passwordHash())
            .param("enabled", account.enabled())
            .param("securityVersion", account.securityVersion())
            .param("updatedAt", Timestamp.from(account.updatedAt()))
            .param("id", account.id())
            .param("version", account.version())
            .update();
    if (rows != 1) {
      throw new IdentityException(IdentityException.Reason.CONFLICT, "账号已被修改，请刷新后重试");
    }
  }

  @Override
  public List<EmployeeAccount> page(String name, int offset, int limit) {
    return jdbc.sql(
            """
            SELECT * FROM identity_employee WHERE strpos(lower(name), lower(:name)) > 0
            ORDER BY id LIMIT :limit OFFSET :offset
            """)
        .param("name", name)
        .param("limit", limit)
        .param("offset", offset)
        .query(JdbcEmployeeRepository::map)
        .list();
  }

  @Override
  public long count(String name) {
    return jdbc.sql(
            "SELECT count(*) FROM identity_employee WHERE strpos(lower(name), lower(?)) > 0")
        .param(name)
        .query(Long.class)
        .single();
  }

  /** 显式重建聚合，避免反射复制绕开领域对象构造约束. */
  static EmployeeAccount map(ResultSet row, int index) throws SQLException {
    return EmployeeAccount.restore(
        row.getLong("id"),
        new EmployeeProfile(
            row.getString("username"),
            row.getString("name"),
            row.getString("phone"),
            row.getString("sex"),
            row.getString("id_number")),
        row.getString("password_hash"),
        EmployeeAccount.Role.valueOf(row.getString("role")),
        row.getBoolean("enabled"),
        row.getLong("security_version"),
        row.getLong("version"),
        row.getTimestamp("created_at").toInstant(),
        row.getTimestamp("updated_at").toInstant());
  }
}
