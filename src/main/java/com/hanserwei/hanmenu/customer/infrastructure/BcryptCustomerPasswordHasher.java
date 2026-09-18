package com.hanserwei.hanmenu.customer.infrastructure;

import com.hanserwei.hanmenu.customer.domain.CustomerPassword;
import com.hanserwei.hanmenu.customer.domain.CustomerPasswordHasher;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** 顾客密码 BCrypt 适配器，认证算法与员工账号实现隔离. */
@Component
class BcryptCustomerPasswordHasher implements CustomerPasswordHasher {
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
  private final String dummyHash = encoder.encode("customer-invalid-credential");

  @Override
  public String encode(CustomerPassword password) {
    return encoder.encode(password.value());
  }

  @Override
  public boolean matches(String rawPassword, String encodedPassword) {
    boolean usable =
        rawPassword != null && rawPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    boolean matched =
        encoder.matches(
            usable ? rawPassword : "invalid",
            encodedPassword == null ? dummyHash : encodedPassword);
    return usable && encodedPassword != null && matched;
  }
}
