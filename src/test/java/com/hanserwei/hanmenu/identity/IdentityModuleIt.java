package com.hanserwei.hanmenu.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanserwei.hanmenu.identity.application.EmployeeAdministration;
import com.hanserwei.hanmenu.identity.application.TokenFactory;
import com.hanserwei.hanmenu.identity.domain.NewPassword;
import com.hanserwei.hanmenu.support.TestDatabase;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 在真实 PostgreSQL、Redis 和完整安全链上验证身份模块及旧客户端契约. */
@ApplicationModuleTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "han-menu.identity.login-max-attempts=4",
      "han-menu.identity.login-max-ip-attempts=10"
    })
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
@TestExecutionListeners(
    listeners = {TestDatabase.Cleanup.class, TestDatabase.RedisCleanup.class},
    mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
class IdentityModuleIt {
  private static final TestDatabase DATABASE = new TestDatabase(IdentityModuleIt.class);
  private static final String ADMIN_PASSWORD = "P1-Admin-password-2026";
  private static final String STAFF_PASSWORD = "P1-Staff-password-2026";
  private static final String NEW_PASSWORD = "Replaced-Password-2026";

  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private JdbcClient jdbc;
  @Autowired private StringRedisTemplate redis;
  @Autowired private EmployeeAdministration administration;
  @Autowired private TokenFactory tokens;

  @Value("${han-menu.identity.redis-key-prefix}")
  private String redisPrefix;

  private String adminToken;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    DATABASE.configure(registry);
  }

  @BeforeEach
  void prepareIsolatedFixtures() throws Exception {
    jdbc.sql(
            "TRUNCATE identity_session, identity_audit, identity_employee RESTART IDENTITY CASCADE")
        .update();
    clearRateLimits();
    administration.bootstrap("admin", new NewPassword(ADMIN_PASSWORD));
    adminToken = login("admin", ADMIN_PASSWORD);
  }

  @Test
  void keepsLegacyContractAndStoresOnlyPasswordAndTokenHashes() throws Exception {
    var me =
        mvc.perform(get("/admin/employee/me").header("token", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(1))
            .andExpect(jsonPath("$.data.role").value("ADMIN"))
            .andExpect(header().exists("X-Request-ID"))
            .andReturn();
    assertThat(body(me).path("data").path("id").asLong()).isEqualTo(1);
    String stored =
        jdbc.sql("SELECT password_hash FROM identity_employee WHERE id = 1")
            .query(String.class)
            .single();
    assertThat(stored).startsWith("$2a$12$").doesNotContain(ADMIN_PASSWORD);
    assertThat(jdbc.sql("SELECT token_hash FROM identity_session").query(String.class).single())
        .isEqualTo(tokens.digest(adminToken))
        .isNotEqualTo(adminToken);
    mvc.perform(get("/admin/employee/page").header("token", adminToken))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].password").doesNotExist())
        .andExpect(jsonPath("$.data.records[0].passwordHash").doesNotExist())
        .andExpect(jsonPath("$.data.records[0].createTime").isString());
  }

  @Test
  void staffCannotEscalateRolesOrManageOtherEmployees() throws Exception {
    long id = createStaff("staff", "普通员工");
    String token = login("staff", STAFF_PASSWORD);
    mvc.perform(get("/admin/employee/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("STAFF"));
    mvc.perform(get("/admin/employee/page").header("token", token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/admin/employee/1").header("token", token)).andExpect(status().isForbidden());
    mvc.perform(get("/admin/employee/status/0").param("id", "1").header("token", token))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/admin/employee")
                .header("token", token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(Map.of("id", id, "username", "staff", "name", "越权"))))
        .andExpect(status().isForbidden());
    assertThat(
            jdbc.sql("SELECT role FROM identity_employee WHERE id = ?")
                .param(id)
                .query(String.class)
                .single())
        .isEqualTo("STAFF");
  }

  @Test
  void logoutRevokesOnlyTheCurrentToken() throws Exception {
    String another = login("admin", ADMIN_PASSWORD);
    mvc.perform(post("/admin/employee/logout").header("token", adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(1));
    mvc.perform(get("/admin/employee/me").header("token", adminToken))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/admin/employee/me").header("token", another)).andExpect(status().isOk());
  }

  @Test
  void disablingAndReenablingCannotReviveOldSessions() throws Exception {
    long id = createStaff("staff", "普通员工");
    String staff = login("staff", STAFF_PASSWORD);
    mvc.perform(
            patch("/admin/employee/{id}/status", id)
                .header("token", adminToken)
                .contentType("application/json")
                .content("{\"status\":0,\"version\":0}"))
        .andExpect(status().isOk());
    mvc.perform(get("/admin/employee/me").header("token", staff))
        .andExpect(status().isUnauthorized());
    loginRequest("staff", STAFF_PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.msg").value("用户名或密码错误"));
    mvc.perform(
            get("/admin/employee/status/1")
                .param("id", Long.toString(id))
                .header("token", adminToken))
        .andExpect(status().isOk())
        .andExpect(header().string("Deprecation", "true"));
    mvc.perform(get("/admin/employee/me").header("token", staff))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/admin/employee/me").header("token", login("staff", STAFF_PASSWORD)))
        .andExpect(status().isOk());
    mvc.perform(get("/admin/employee/status/0").param("id", "1").header("token", adminToken))
        .andExpect(status().isConflict());
  }

  @Test
  void changingPasswordRequiresOldPasswordAndRevokesAllSessions() throws Exception {
    createStaff("staff", "普通员工");
    String first = login("staff", STAFF_PASSWORD);
    String second = login("staff", STAFF_PASSWORD);
    mvc.perform(
            put("/admin/employee/password")
                .header("token", first)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("oldPassword", "wrong", "newPassword", NEW_PASSWORD))))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            put("/admin/employee/password")
                .header("token", first)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("oldPassword", STAFF_PASSWORD, "newPassword", NEW_PASSWORD))))
        .andExpect(status().isOk());
    mvc.perform(get("/admin/employee/me").header("token", first))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/admin/employee/me").header("token", second))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/admin/employee/me").header("token", login("staff", NEW_PASSWORD)))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsMissingExpiredForgedAmbiguousAndCookieOnlyCredentials() throws Exception {
    mvc.perform(get("/admin/employee/page"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(0));
    mvc.perform(get("/admin/employee/me").cookie(new Cookie("token", adminToken)))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/admin/employee/me").header("token", "hme_" + "a".repeat(43)))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/admin/employee/me")
                .header("token", adminToken)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/admin/employee/me").header("token", adminToken, adminToken))
        .andExpect(status().isBadRequest());
    jdbc.sql("UPDATE identity_session SET expires_at = ?")
        .param(Timestamp.from(Instant.now().minusSeconds(1)))
        .update();
    mvc.perform(get("/admin/employee/me").header("token", adminToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void supportsEditingPaginationAndConflictsWithoutMassAssignment() throws Exception {
    long id = createStaff("staff", "百分比%员工");
    mvc.perform(
            get("/admin/employee/page")
                .header("token", adminToken)
                .param("page", "1")
                .param("pageSize", "1")
                .param("name", "%"))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].id").value(id));
    String update =
        json.writeValueAsString(
            Map.of(
                "id",
                id,
                "username",
                "staff",
                "name",
                "修改后的员工",
                "version",
                0,
                "role",
                "ADMIN",
                "status",
                0));
    mvc.perform(
            put("/admin/employee")
                .header("token", adminToken)
                .contentType("application/json")
                .content(update))
        .andExpect(status().isOk());
    mvc.perform(
            put("/admin/employee")
                .header("token", adminToken)
                .contentType("application/json")
                .content(update))
        .andExpect(status().isConflict());
    mvc.perform(get("/admin/employee/{id}", id).header("token", adminToken))
        .andExpect(jsonPath("$.data.role").value("STAFF"))
        .andExpect(jsonPath("$.data.status").value(1));
    mvc.perform(get("/admin/employee/99999").header("token", adminToken))
        .andExpect(status().isNotFound());
    mvc.perform(get("/admin/employee/page").header("token", adminToken).param("pageSize", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(0));
    mvc.perform(
            post("/admin/employee")
                .header("token", adminToken)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", "STAFF", "name", "重复员工"))))
        .andExpect(status().isConflict());
  }

  @Test
  void enforcesAtomicRedisQuotaWithExpiryAndIgnoresSpoofedForwardedAddresses() throws Exception {
    for (int attempt = 0; attempt < 4; attempt++) {
      mvc.perform(
              post("/admin/employee/login")
                  .header("X-Forwarded-For", "192.0.2." + attempt)
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(Map.of("username", "unknown", "password", "wrong"))))
          .andExpect(status().isUnauthorized());
    }
    loginRequest("unknown", "wrong")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"));
    assertThat(rateKeys()).hasSize(3);
    for (String key : rateKeys()) {
      assertThat(key).doesNotContain("unknown", "127.0.0.1");
      assertThat(redis.getExpire(key)).isBetween(1L, 60L);
    }
    clearRateLimits();
    loginRequest("unknown", "wrong").andExpect(status().isUnauthorized());
  }

  @Test
  void databaseFailureDoesNotBypassAuthentication() throws Exception {
    jdbc.sql("ALTER TABLE identity_session RENAME TO unavailable_session").update();
    try {
      mvc.perform(get("/admin/employee/me").header("token", adminToken))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.msg").value("认证服务暂不可用"));
    } finally {
      jdbc.sql("ALTER TABLE unavailable_session RENAME TO identity_session").update();
    }
  }

  @Test
  void rotatingUsernamesCannotBypassIpQuota() throws Exception {
    clearRateLimits();
    for (int attempt = 0; attempt < 10; attempt++) {
      loginRequest("unknown_" + attempt, "wrong").andExpect(status().isUnauthorized());
    }
    loginRequest("another_unknown", "wrong").andExpect(status().isTooManyRequests());
  }

  @Test
  void staleHeaderDoesNotPreventLoggingInAgain() throws Exception {
    mvc.perform(
            post("/admin/employee/login")
                .header("token", "hme_" + "z".repeat(43))
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("username", "admin", "password", ADMIN_PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(1));
  }

  @Test
  void validationAndFrameworkErrorsKeepLegacyEnvelopeWithoutRejectedValues(CapturedOutput output)
      throws Exception {
    String rejectedPassword = "private-rejected-value-" + "x".repeat(130);
    var result =
        loginRequest("admin", rejectedPassword)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
    assertThat(result.getResponse().getContentAsString()).doesNotContain(rejectedPassword);
    mvc.perform(put("/admin/employee/login").header("token", adminToken))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value(0));
    assertThat(output.getAll()).doesNotContain(rejectedPassword);
  }

  @Test
  void auditAndLogsExcludeCredentialsAndPersonalData(CapturedOutput output) throws Exception {
    createStaff("staff", "不应进入日志的姓名");
    String token = login("staff", STAFF_PASSWORD);
    loginRequest("missing", "wrong").andExpect(status().isUnauthorized());
    String audit =
        String.join(
            "",
            jdbc.sql("SELECT row_to_json(a)::text FROM identity_audit a")
                .query(String.class)
                .list());
    assertThat(audit)
        .contains("LOGIN", "FAILURE", "CREATE_EMPLOYEE")
        .doesNotContain(
            ADMIN_PASSWORD, STAFF_PASSWORD, adminToken, token, "不应进入日志的姓名", "13800138000");
    assertThat(output.getAll())
        .contains("security_audit")
        .doesNotContain(
            ADMIN_PASSWORD, STAFF_PASSWORD, adminToken, token, "不应进入日志的姓名", "13800138000");
  }

  @Test
  void publishesApiDocumentationAndDeniesUnimplementedBusinessRoutes() throws Exception {
    var document = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    assertThat(body(document).path("paths").has("/admin/employee/login")).isTrue();
    assertThat(body(document).path("components").path("securitySchemes").has("employeeToken"))
        .isTrue();
    mvc.perform(get("/user/order/historyOrders").header("token", adminToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  void bootstrapDoesNotResetCredentialsOrCreateAnotherAdministrator() throws Exception {
    String original =
        jdbc.sql("SELECT password_hash FROM identity_employee WHERE id = 1")
            .query(String.class)
            .single();
    administration.bootstrap("another_admin", new NewPassword(NEW_PASSWORD));
    assertThat(jdbc.sql("SELECT count(*) FROM identity_employee").query(Long.class).single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("SELECT password_hash FROM identity_employee WHERE id = 1")
                .query(String.class)
                .single())
        .isEqualTo(original);
    login("admin", ADMIN_PASSWORD);
  }

  @Test
  void generatesUniqueInitialPasswordsWithoutLegacyDefault() throws Exception {
    var result =
        mvc.perform(
                post("/admin/employee")
                    .header("token", adminToken)
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(Map.of("username", "generated", "name", "新员工"))))
            .andExpect(status().isOk())
            .andReturn();
    String initial = body(result).path("data").path("initialPassword").asString();
    assertThat(initial).hasSize(32);
    login("generated", initial);
  }

  private long createStaff(String username, String name) throws Exception {
    var result =
        mvc.perform(
                post("/admin/employee")
                    .header("token", adminToken)
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "username",
                                username,
                                "name",
                                name,
                                "phone",
                                "13800138000",
                                "sex",
                                "1",
                                "idNumber",
                                "110101199001010010",
                                "password",
                                STAFF_PASSWORD,
                                "role",
                                "ADMIN"))))
            .andExpect(status().isOk())
            .andReturn();
    return body(result).path("data").path("id").asLong();
  }

  private org.springframework.test.web.servlet.ResultActions loginRequest(
      String username, String password) throws Exception {
    return mvc.perform(
        post("/admin/employee/login")
            .contentType("application/json")
            .content(json.writeValueAsString(Map.of("username", username, "password", password))));
  }

  private String login(String username, String password) throws Exception {
    var result =
        loginRequest(username, password)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(1))
            .andExpect(jsonPath("$.data.userName").value(username))
            .andExpect(
                header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andReturn();
    String token = body(result).path("data").path("token").asString();
    assertThat(token).matches("hme_[A-Za-z0-9_-]{43}");
    return token;
  }

  private JsonNode body(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  private List<String> rateKeys() {
    var result = new ArrayList<String>();
    try (var cursor =
        redis.scan(ScanOptions.scanOptions().match(redisPrefix + "*").count(100).build())) {
      cursor.forEachRemaining(result::add);
    }
    return result;
  }

  private void clearRateLimits() {
    var keys = rateKeys();
    if (!keys.isEmpty()) {
      redis.delete(keys);
    }
  }
}
