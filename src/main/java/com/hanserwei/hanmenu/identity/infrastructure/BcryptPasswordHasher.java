package com.hanserwei.hanmenu.identity.infrastructure;

import com.hanserwei.hanmenu.identity.domain.NewPassword;
import com.hanserwei.hanmenu.identity.domain.PasswordHasher;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** 用 BCrypt 的工作因子 12 存储密码，并对不存在的账号执行等成本比较. */
@Component
class BcryptPasswordHasher implements PasswordHasher {
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
  private final String dummyHash = encoder.encode("only-for-invalid-credential-comparison");

  @Override
  public String encode(NewPassword password) {
    return encoder.encode(password.value());
  }

  @Override
  public boolean matches(String rawPassword, String encodedPassword) {
    boolean usable =
        rawPassword != null && rawPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    boolean matched =
        encoder.matches(
            usable ? rawPassword : "invalid-credential",
            encodedPassword == null ? dummyHash : encodedPassword);
    return usable && encodedPassword != null && matched;
  }
}
