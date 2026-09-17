package com.hanserwei.hanmenu.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanserwei.hanmenu.identity.application.EmployeeAdministration;
import com.hanserwei.hanmenu.identity.application.TokenFactory;
import com.hanserwei.hanmenu.identity.domain.EmployeeProfile;
import com.hanserwei.hanmenu.identity.domain.EmployeeRepository;
import com.hanserwei.hanmenu.identity.domain.NewPassword;
import com.hanserwei.hanmenu.support.TestDatabase;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 在真实 PostgreSQL、Redis 与 JPA 事务中验证全新 HTTP 契约及认证权限边界. */
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
  @Autowired private EmployeeRepository employees;
  @Autowired private TokenFactory tokens;

  @Value("${han-menu.identity.redis-key-prefix}")
  private String redisPrefix;

  private String adminToken;
  private UUID adminId;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    DATABASE.configure(registry);
  }

  @BeforeEach
  void fixtures() throws Exception {
    jdbc.sql("TRUNCATE identity_session, identity_audit, identity_employee CASCADE").update();
    clearRateLimits();
    administration.bootstrap("admin", new NewPassword(ADMIN_PASSWORD));
    adminId = employees.findByUsername("admin").orElseThrow().id();
    adminToken = login("admin", ADMIN_PASSWORD);
  }

  @Test
  void createsResourceBasedSessionsAndStoresOnlyHashes() throws Exception {
    var me =
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(header().exists("X-Request-ID"))
            .andReturn();
    assertThat(UUID.fromString(body(me).path("id").asString())).isEqualTo(adminId);
    assertThat(
            jdbc.sql("SELECT password_hash FROM identity_employee WHERE id = ?")
                .param(adminId)
                .query(String.class)
                .single())
        .startsWith("$2a$12$")
        .doesNotContain(ADMIN_PASSWORD);
    assertThat(jdbc.sql("SELECT token_hash FROM identity_session").query(String.class).single())
        .isEqualTo(tokens.digest(adminToken));
    mvc.perform(get("/api/v1/employees").header("Authorization", bearer(adminToken)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
  }

  @Test
  void enforcesRolesAndRejectsUndeclaredPrivilegeFields() throws Exception {
    createStaff("staff", "普通员工");
    String token = login("staff", STAFF_PASSWORD);
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("STAFF"));
    mvc.perform(get("/api/v1/employees").header("Authorization", bearer(token)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
    mvc.perform(get("/api/v1/employees/{id}", adminId).header("Authorization", bearer(token)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/employees")
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "username",
                            "injected",
                            "displayName",
                            "非法角色",
                            "password",
                            STAFF_PASSWORD,
                            "role",
                            "ADMIN"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void deletingCurrentSessionDoesNotDeleteOtherSessions() throws Exception {
    String another = login("admin", ADMIN_PASSWORD);
    mvc.perform(delete("/api/v1/sessions/current").header("Authorization", bearer(adminToken)))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(adminToken)))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(another)))
        .andExpect(status().isOk());
  }

  @Test
  void reenablingDoesNotReviveOldSessions() throws Exception {
    UUID id = createStaff("staff", "普通员工");
    String token = login("staff", STAFF_PASSWORD);
    changeStatus(id, "DISABLED", 0).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
        .andExpect(status().isUnauthorized());
    loginRequest("staff", STAFF_PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("用户名或密码错误"));
    changeStatus(id, "ACTIVE", 1).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(login("staff", STAFF_PASSWORD))))
        .andExpect(status().isOk());
    changeStatus(adminId, "DISABLED", 0).andExpect(status().isConflict());
  }

  @Test
  void changingPasswordRequiresCurrentPasswordAndRevokesAllSessions() throws Exception {
    createStaff("staff", "普通员工");
    String first = login("staff", STAFF_PASSWORD);
    String second = login("staff", STAFF_PASSWORD);
    password(first, "wrong", NEW_PASSWORD).andExpect(status().isUnauthorized());
    password(first, STAFF_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(first)))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(second)))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(login("staff", NEW_PASSWORD))))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsExpiredForgedCookieAndOldHeaderAuthentication() throws Exception {
    mvc.perform(get("/api/v1/me").cookie(new Cookie("token", adminToken)))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me").header("token", adminToken)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me").header("Authorization", bearer("hme_" + "x".repeat(43))))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(adminToken), bearer(adminToken)))
        .andExpect(status().isBadRequest());
    jdbc.sql("UPDATE identity_session SET expires_at = ?")
        .param(Timestamp.from(Instant.now().minusSeconds(1)))
        .update();
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(adminToken)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void supportsStablePaginationAndMandatoryVersionedUpdates() throws Exception {
    UUID id = createStaff("staff", "百分比%员工");
    mvc.perform(
            get("/api/v1/employees")
                .header("Authorization", bearer(adminToken))
                .param("page", "0")
                .param("size", "1")
                .param("name", "%"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.items[0].id").value(id.toString()));
    String update =
        json.writeValueAsString(Map.of("username", "staff", "displayName", "新姓名", "version", 0));
    mvc.perform(
            put("/api/v1/employees/{id}", id)
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content(update))
        .andExpect(status().isNoContent());
    mvc.perform(
            put("/api/v1/employees/{id}", id)
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content(update))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    mvc.perform(
            put("/api/v1/employees/{id}", id)
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content("{\"username\":\"staff\",\"displayName\":\"未带版本\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/employees/{id}", UUID.randomUUID())
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/employees")
                .header("Authorization", bearer(adminToken))
                .param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void ormRepositoryRejectsStaleAggregateUpdates() throws Exception {
    UUID id = createStaff("staff", "原始姓名");
    var first = employees.findById(id).orElseThrow();
    var stale = employees.findById(id).orElseThrow();
    first.reviseProfile(new EmployeeProfile("staff", "第一位编辑者", ""), Instant.now());
    employees.update(first);
    stale.reviseProfile(new EmployeeProfile("staff", "陈旧编辑者", ""), Instant.now());
    assertThatThrownBy(() -> employees.update(stale))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    assertThat(employees.findById(id).orElseThrow().profile().displayName()).isEqualTo("第一位编辑者");
  }

  @Test
  void redisLimitsHaveExpiryAndCannotTrustForwardedHeaders() throws Exception {
    for (int index = 0; index < 4; index++) {
      mvc.perform(
              post("/api/v1/sessions")
                  .header("X-Forwarded-For", "192.0.2." + index)
                  .contentType("application/json")
                  .content(credentials("unknown", "wrong")))
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
  }

  @Test
  void rotatingUsernamesCannotBypassIpQuota() throws Exception {
    clearRateLimits();
    for (int index = 0; index < 10; index++) {
      loginRequest("unknown_" + index, "wrong").andExpect(status().isUnauthorized());
    }
    loginRequest("another_unknown", "wrong").andExpect(status().isTooManyRequests());
  }

  @Test
  void databaseFailureCannotBypassAuthentication() throws Exception {
    jdbc.sql("ALTER TABLE identity_session RENAME TO unavailable_session").update();
    try {
      mvc.perform(get("/api/v1/me").header("Authorization", bearer(adminToken)))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("UNAVAILABLE"));
    } finally {
      jdbc.sql("ALTER TABLE unavailable_session RENAME TO identity_session").update();
    }
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
        .contains("LOGIN", "false", "CREATE_EMPLOYEE")
        .doesNotContain(
            ADMIN_PASSWORD, STAFF_PASSWORD, adminToken, token, "不应进入日志的姓名", "13800138000");
    assertThat(output.getAll())
        .contains("security_audit")
        .doesNotContain(
            ADMIN_PASSWORD, STAFF_PASSWORD, adminToken, token, "不应进入日志的姓名", "13800138000");
  }

  @Test
  void publishesOnlyModernApiAndUsesProblemDetails() throws Exception {
    var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    var paths = body(result).path("paths");
    assertThat(paths.has("/api/v1/sessions")).isTrue();
    assertThat(paths.has("/admin/employee/login")).isFalse();
    assertThat(body(result).path("components").path("securitySchemes").has("employeeToken"))
        .isFalse();
    var denied =
        mvc.perform(get("/api/v1/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(
                header()
                    .string(
                        "Content-Type",
                        org.hamcrest.Matchers.startsWith("application/problem+json")))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andReturn();
    assertThat(body(denied).path("traceId").asString())
        .isEqualTo(denied.getResponse().getHeader("X-Request-ID"));
    assertThat(body(denied).path("instance").asString()).isEqualTo("/api/v1/me");
  }

  @Test
  void bootstrapDoesNotResetExistingNewApplicationCredentials() {
    String original = employees.findById(adminId).orElseThrow().passwordHash();
    administration.bootstrap("another_admin", new NewPassword(NEW_PASSWORD));
    assertThat(jdbc.sql("SELECT count(*) FROM identity_employee").query(Long.class).single())
        .isEqualTo(1);
    assertThat(employees.findById(adminId).orElseThrow().passwordHash()).isEqualTo(original);
  }

  @Test
  void validatesRequiredPasswordAndDoesNotEchoRejectedValues(CapturedOutput output)
      throws Exception {
    String rejected = "private-rejected-value-" + "x".repeat(130);
    var result = loginRequest("admin", rejected).andExpect(status().isBadRequest()).andReturn();
    assertThat(result.getResponse().getContentAsString()).doesNotContain(rejected);
    mvc.perform(
            post("/api/v1/employees")
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content("{\"username\":\"missing_password\",\"displayName\":\"员工\"}"))
        .andExpect(status().isBadRequest());
    assertThat(output.getAll()).doesNotContain(rejected);
  }

  @Test
  void duplicateAccountsReturnConflictWithoutLeakingDatabaseDetails(CapturedOutput output)
      throws Exception {
    String username = "private_duplicate_account";
    createStaff(username, "私有姓名");
    var result =
        mvc.perform(
                post("/api/v1/employees")
                    .header("Authorization", bearer(adminToken))
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "username",
                                username,
                                "displayName",
                                "私有姓名",
                                "password",
                                STAFF_PASSWORD))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain(username, "INSERT", "password_hash");
    assertThat(output.getAll()).doesNotContain(username, "私有姓名", STAFF_PASSWORD);
  }

  @Test
  void reauthenticationCanIgnoreAnExpiredBearerOnSessionCreation() throws Exception {
    mvc.perform(
            post("/api/v1/sessions")
                .header("Authorization", bearer("hme_" + "z".repeat(43)))
                .contentType("application/json")
                .content(credentials("admin", ADMIN_PASSWORD)))
        .andExpect(status().isCreated());
  }

  private UUID createStaff(String username, String displayName) throws Exception {
    var result =
        mvc.perform(
                post("/api/v1/employees")
                    .header("Authorization", bearer(adminToken))
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "username",
                                username,
                                "displayName",
                                displayName,
                                "phone",
                                "13800138000",
                                "password",
                                STAFF_PASSWORD))))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn();
    return UUID.fromString(body(result).path("id").asString());
  }

  private ResultActions changeStatus(UUID id, String value, long version) throws Exception {
    return mvc.perform(
        patch("/api/v1/employees/{id}/status", id)
            .header("Authorization", bearer(adminToken))
            .contentType("application/json")
            .content(json.writeValueAsString(Map.of("status", value, "version", version))));
  }

  private ResultActions password(String token, String current, String replacement)
      throws Exception {
    return mvc.perform(
        put("/api/v1/me/password")
            .header("Authorization", bearer(token))
            .contentType("application/json")
            .content(
                json.writeValueAsString(
                    Map.of("currentPassword", current, "newPassword", replacement))));
  }

  private String credentials(String username, String password) throws Exception {
    return json.writeValueAsString(Map.of("username", username, "password", password));
  }

  private ResultActions loginRequest(String username, String password) throws Exception {
    return mvc.perform(
        post("/api/v1/sessions")
            .contentType("application/json")
            .content(credentials(username, password)));
  }

  private String login(String username, String password) throws Exception {
    var result =
        loginRequest(username, password)
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/sessions/current"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andReturn();
    String token = body(result).path("accessToken").asString();
    assertThat(token).matches("hme_[A-Za-z0-9_-]{43}");
    assertThat(Instant.parse(body(result).path("expiresAt").asString())).isAfter(Instant.now());
    return token;
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private JsonNode body(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  private List<String> rateKeys() {
    var keys = new ArrayList<String>();
    try (var cursor =
        redis.scan(ScanOptions.scanOptions().match(redisPrefix + "*").count(100).build())) {
      cursor.forEachRemaining(keys::add);
    }
    return keys;
  }

  private void clearRateLimits() {
    var keys = rateKeys();
    if (!keys.isEmpty()) {
      redis.delete(keys);
    }
  }
}
