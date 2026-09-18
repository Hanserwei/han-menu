package com.hanserwei.hanmenu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanserwei.hanmenu.catalog.application.CatalogAdministration;
import com.hanserwei.hanmenu.catalog.application.PublicCatalog;
import com.hanserwei.hanmenu.identity.application.EmployeeAdministration;
import com.hanserwei.hanmenu.identity.application.EmployeeAuthentication;
import com.hanserwei.hanmenu.identity.domain.EmployeeProfile;
import com.hanserwei.hanmenu.identity.domain.NewPassword;
import com.hanserwei.hanmenu.support.TestDatabase;
import jakarta.persistence.EntityManagerFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P2 纵向验收，使用完整安全链、真实 PostgreSQL/Redis/RustFS，资源均隔离并清理. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@TestExecutionListeners(
    listeners = {TestDatabase.Cleanup.class, TestDatabase.RedisCleanup.class},
    mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
class CatalogAndShopIt {
  private static final TestDatabase DATABASE = new TestDatabase(CatalogAndShopIt.class);
  private static final String PASSWORD = "P2-Admin-password-2026";
  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private JdbcClient jdbc;
  @Autowired private StringRedisTemplate redis;
  @Autowired private S3Client storage;
  @Autowired private EmployeeAdministration employees;
  @Autowired private EmployeeAuthentication authentication;
  @Autowired private CatalogAdministration catalog;
  @Autowired private PublicCatalog menu;
  @Autowired private TransactionTemplate transactions;
  @Autowired private EntityManagerFactory entityManagerFactory;

  @Value("${han-menu.catalog.storage.bucket}")
  private String bucket;

  @Value("${han-menu.catalog.storage.key-prefix}")
  private String objectPrefix;

  @Value("${han-menu.catalog.cache-prefix}")
  private String cachePrefix;

  private String token;
  private com.hanserwei.hanmenu.identity.api.StaffIdentity actor;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    DATABASE.configure(registry);
  }

  /** 独立数据库与 Redis 前缀，S3 仅使用专用测试桶及本上下文前缀. */
  @BeforeEach
  void fixtures() {
    if (!bucket.endsWith("-test") || !objectPrefix.startsWith("han-menu-test/")) {
      throw new IllegalStateException("拒绝操作非测试对象存储空间");
    }
    try {
      storage.headBucket(request -> request.bucket(bucket));
    } catch (S3Exception exception) {
      if (exception.statusCode() != 404) {
        throw exception;
      }
      storage.createBucket(request -> request.bucket(bucket));
    }
    jdbc.sql(
            "TRUNCATE catalog_meal_component, catalog_product, catalog_category, catalog_image,"
                + " identity_session, identity_audit, identity_employee CASCADE")
        .update();
    jdbc.sql("UPDATE catalog_revision SET revision = 0").update();
    jdbc.sql("UPDATE shop_profile SET name='Han Menu', phone='', address='', open=false, version=0")
        .update();
    clearCache();
    employees.bootstrap("admin", new NewPassword(PASSWORD));
    var login = authentication.login("admin", PASSWORD, UUID.randomUUID().toString());
    token = login.token();
    actor = login.identity();
  }

  @AfterEach
  void cleanupObjects() {
    if (bucket.endsWith("-test") && objectPrefix.startsWith("han-menu-test/")) {
      storage
          .listObjectsV2Paginator(request -> request.bucket(bucket).prefix(objectPrefix))
          .contents()
          .forEach(
              object -> storage.deleteObject(request -> request.bucket(bucket).key(object.key())));
    }
  }

  @Test
  void productLifecycleChangesPublicCacheOnlyAfterCommit() throws Exception {
    String category = category("DISH", "主食");
    String product = product("DISH", details(category, "面条", List.of(), List.of(), null));
    publicMenu().andExpect(jsonPath("$.totalElements").value(0));
    sale(product, "ON_SALE", 0)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    publicMenu().andExpect(jsonPath("$.items[0].name").value("面条"));
    assertThat(cacheKeys()).isNotEmpty();
    for (String key : cacheKeys()) {
      assertThat(redis.getExpire(key)).isBetween(1L, 120L);
    }
    mvc.perform(
            put("/api/v1/catalog/products/{id}", product)
                .header("Authorization", bearer())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "details",
                            details(category, "新名", List.of(), List.of(), null),
                            "version",
                            1))))
        .andExpect(status().isConflict());
    sale(product, "OFF_SALE", 0).andExpect(status().isConflict());
    long revision = jdbc.sql("SELECT revision FROM catalog_revision").query(Long.class).single();
    transactions.executeWithoutResult(
        tx -> {
          catalog.changeSale(actor, UUID.fromString(product), false, 1);
          tx.setRollbackOnly();
        });
    assertThat(jdbc.sql("SELECT revision FROM catalog_revision").query(Long.class).single())
        .isEqualTo(revision);
    publicMenu().andExpect(jsonPath("$.totalElements").value(1));
    sale(product, "OFF_SALE", 1).andExpect(status().isOk());
    publicMenu().andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            put("/api/v1/catalog/products/{id}", product)
                .header("Authorization", bearer())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "details",
                            details(category, "改价面条", List.of(), List.of(), null),
                            "version",
                            2))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("改价面条"));
    sale(product, "ON_SALE", 3).andExpect(status().isOk());
    publicMenu().andExpect(jsonPath("$.items[0].name").value("改价面条"));
  }

  @Test
  void mealPublicationRequiresSaleableDishesAndFixedFlavorChoices() throws Exception {
    String dishCategory = category("DISH", "菜品");
    String mealCategory = category("SET_MEAL", "套餐");
    var flavors = List.of(Map.of("name", "辣度", "options", List.of("微辣", "不辣"), "required", true));
    String dish = product("DISH", details(dishCategory, "菜", flavors, List.of(), null));
    createProduct(
            "SET_MEAL",
            details(
                mealCategory,
                "错误口味套餐",
                List.of(),
                List.of(Map.of("dishId", dish, "quantity", 1, "selections", Map.of())),
                null))
        .andExpect(status().isBadRequest());
    String meal =
        product(
            "SET_MEAL",
            details(
                mealCategory,
                "一人餐",
                List.of(),
                List.of(Map.of("dishId", dish, "quantity", 2, "selections", Map.of("辣度", "微辣"))),
                null));
    sale(meal, "ON_SALE", 0).andExpect(status().isConflict());
    sale(dish, "ON_SALE", 0).andExpect(status().isOk());
    sale(meal, "ON_SALE", 0).andExpect(status().isOk());
    sale(dish, "OFF_SALE", 1).andExpect(status().isConflict());
    publicMenu().andExpect(jsonPath("$.totalElements").value(2));
    sale(meal, "OFF_SALE", 1).andExpect(status().isOk());
    sale(dish, "OFF_SALE", 1).andExpect(status().isOk());
    mvc.perform(
            delete("/api/v1/catalog/products/{id}", dish)
                .param("version", "2")
                .header("Authorization", bearer()))
        .andExpect(status().isConflict());
    mvc.perform(
            delete("/api/v1/catalog/products/{id}", meal)
                .param("version", "2")
                .header("Authorization", bearer()))
        .andExpect(status().isNoContent());
    mvc.perform(
            delete("/api/v1/catalog/products/{id}", dish)
                .param("version", "2")
                .header("Authorization", bearer()))
        .andExpect(status().isNoContent());
  }

  @Test
  void categoryTypeReferencesAndVersionAreProtected() throws Exception {
    String category = category("DISH", "饮品");
    createCategory("DISH", "饮品").andExpect(status().isConflict());
    createProduct("SET_MEAL", details(category, "错误类型", List.of(), List.of(), null))
        .andExpect(status().isBadRequest());
    String product = product("DISH", details(category, "茶", List.of(), List.of(), null));
    sale(product, "ON_SALE", 0).andExpect(status().isOk());
    updateCategory(category, false, 0).andExpect(status().isConflict());
    mvc.perform(
            delete("/api/v1/catalog/categories/{id}", category)
                .param("version", "0")
                .header("Authorization", bearer()))
        .andExpect(status().isConflict());
    sale(product, "OFF_SALE", 1).andExpect(status().isOk());
    updateCategory(category, false, 0).andExpect(status().isOk());
    sale(product, "ON_SALE", 2).andExpect(status().isConflict());
    mvc.perform(get("/api/v1/menu/categories")).andExpect(jsonPath("$").isEmpty());
    updateCategory(category, true, 0).andExpect(status().isConflict());
  }

  @Test
  void publicReadsWorkButStaffAndAnonymousCannotManageCatalog() throws Exception {
    mvc.perform(get("/api/v1/storefront")).andExpect(status().isOk());
    publicMenu().andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/catalog/categories")
                .contentType("application/json")
                .content("{\"kind\":\"DISH\",\"name\":\"未认证\",\"sortOrder\":0}"))
        .andExpect(status().isUnauthorized());
    employees.create(actor, new EmployeeProfile("staff", "员工", ""), new NewPassword(PASSWORD));
    String staff = authentication.login("staff", PASSWORD, "staff-source").token();
    mvc.perform(get("/api/v1/catalog/products").header("Authorization", "Bearer " + staff))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/v1/shop/status")
                .header("Authorization", "Bearer " + staff)
                .contentType("application/json")
                .content("{\"status\":\"OPEN\",\"version\":0}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void shopNeedsCompleteConfigurationAndPersistsOpeningState() throws Exception {
    mvc.perform(
            patch("/api/v1/shop/status")
                .header("Authorization", bearer())
                .contentType("application/json")
                .content("{\"status\":\"OPEN\",\"version\":0}"))
        .andExpect(status().isConflict());
    mvc.perform(
            put("/api/v1/shop")
                .header("Authorization", bearer())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "name",
                            "测试门店",
                            "phone",
                            "13800138000",
                            "address",
                            "测试地址",
                            "version",
                            0))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(
            patch("/api/v1/shop/status")
                .header("Authorization", bearer())
                .contentType("application/json")
                .content("{\"status\":\"OPEN\",\"version\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
    clearCache();
    mvc.perform(get("/api/v1/storefront")).andExpect(jsonPath("$.status").value("OPEN"));
    assertThat(jdbc.sql("SELECT open FROM shop_profile").query(Boolean.class).single()).isTrue();
    mvc.perform(
            patch("/api/v1/shop/status")
                .header("Authorization", bearer())
                .contentType("application/json")
                .content("{\"status\":\"CLOSED\",\"version\":1}"))
        .andExpect(status().isConflict());
  }

  @Test
  void imageUploadUsesPrivateStorageAndCompensatesDatabaseFailure() throws Exception {
    byte[] png = png();
    var result =
        mvc.perform(
                multipart("/api/v1/catalog/images")
                    .file(new MockMultipartFile("file", "../../wrong.svg", "text/plain", png))
                    .header("Authorization", bearer()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mediaType").value("image/png"))
            .andReturn();
    String id = body(result).path("id").asString();
    mvc.perform(get("/api/v1/menu/images/{id}", id)).andExpect(status().isNotFound());
    var signed =
        mvc.perform(get("/api/v1/catalog/images/{id}", id).header("Authorization", bearer()))
            .andExpect(status().isOk())
            .andReturn();
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      URI url = URI.create(body(signed).path("url").asString());
      var response =
          client.send(HttpRequest.newBuilder(url).GET().build(), BodyHandlers.ofByteArray());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo(png);
      URI unsigned = new URI(url.getScheme(), url.getAuthority(), url.getPath(), null, null);
      assertThat(
              client
                  .send(HttpRequest.newBuilder(unsigned).GET().build(), BodyHandlers.discarding())
                  .statusCode())
          .isEqualTo(403);
    }
    String category = category("DISH", "图片分类");
    String product = product("DISH", details(category, "图片商品", List.of(), List.of(), id));
    sale(product, "ON_SALE", 0).andExpect(status().isOk());
    mvc.perform(get("/api/v1/menu/images/{id}", id)).andExpect(status().isOk());
    long before = objectCount();
    jdbc.sql("ALTER TABLE catalog_image RENAME TO unavailable_image").update();
    try {
      mvc.perform(
              multipart("/api/v1/catalog/images")
                  .file(new MockMultipartFile("file", "test.png", "image/png", png))
                  .header("Authorization", bearer()))
          .andExpect(status().isServiceUnavailable());
      assertThat(objectCount()).isEqualTo(before);
    } finally {
      jdbc.sql("ALTER TABLE unavailable_image RENAME TO catalog_image").update();
    }
  }

  @Test
  void rejectsFakeOrOversizedImagesAndUnknownOrMissingProductFields() throws Exception {
    mvc.perform(
            multipart("/api/v1/catalog/images")
                .file(new MockMultipartFile("file", "fake.png", "image/png", "not png".getBytes()))
                .header("Authorization", bearer()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart("/api/v1/catalog/images")
                .file(
                    new MockMultipartFile(
                        "file", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]))
                .header("Authorization", bearer()))
        .andExpect(status().isBadRequest());
    assertThat(objectCount()).isZero();
    mvc.perform(
            post("/api/v1/catalog/products")
                .header("Authorization", bearer())
                .contentType("application/json")
                .content("{\"kind\":\"DISH\",\"details\":null}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/catalog/categories")
                .header("Authorization", bearer())
                .contentType("application/json")
                .content("{\"kind\":\"DISH\",\"name\":\"分类\",\"unexpected\":1}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void corruptCacheFallsBackToDatabaseAndGetsReplaced() throws Exception {
    String category = category("DISH", "缓存分类");
    String product = product("DISH", details(category, "缓存菜品", List.of(), List.of(), null));
    sale(product, "ON_SALE", 0).andExpect(status().isOk());
    publicMenu().andExpect(status().isOk());
    assertThat(cacheKeys()).hasSize(1);
    redis.opsForValue().set(cacheKeys().getFirst(), "not-json");
    publicMenu().andExpect(jsonPath("$.items[0].name").value("缓存菜品"));
    assertThat(redis.opsForValue().get(cacheKeys().getFirst())).contains("缓存菜品");
    redis.opsForValue().set(cacheKeys().getFirst(), "null");
    publicMenu().andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void publicCacheHitReadsOnlyRevisionAndPagedProductsAvoidCollectionPagination() throws Exception {
    String category = category("DISH", "分页分类");
    for (int index = 0; index < 4; index++) {
      String product = product("DISH", details(category, "菜品" + index, List.of(), List.of(), null));
      sale(product, "ON_SALE", 0).andExpect(status().isOk());
    }
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    boolean previouslyEnabled = statistics.isStatisticsEnabled();
    try {
      statistics.setStatisticsEnabled(true);
      statistics.clear();
      assertThat(menu.products(null, null, 0, 2).items()).hasSize(2);
      // 修订查询、分页、计数、当前页明细抓取，不按商品数量增加查询次数。
      assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
      statistics.clear();
      assertThat(menu.products(null, null, 0, 2).totalElements()).isEqualTo(4);
      assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    } finally {
      statistics.setStatisticsEnabled(previouslyEnabled);
    }
  }

  @Test
  void concurrentMenuChangesCannotPublishMealWithUnavailableDish() throws Exception {
    String dishCategory = category("DISH", "并发菜品");
    String mealCategory = category("SET_MEAL", "并发套餐");
    String dish = product("DISH", details(dishCategory, "菜", List.of(), List.of(), null));
    sale(dish, "ON_SALE", 0).andExpect(status().isOk());
    String meal =
        product(
            "SET_MEAL",
            details(
                mealCategory,
                "套餐",
                List.of(),
                List.of(Map.of("dishId", dish, "quantity", 1, "selections", Map.of())),
                null));
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var publish =
          executor.submit(
              () -> {
                gate.await();
                return sale(meal, "ON_SALE", 0).andReturn().getResponse().getStatus();
              });
      var stop =
          executor.submit(
              () -> {
                gate.await();
                return sale(dish, "OFF_SALE", 1).andReturn().getResponse().getStatus();
              });
      gate.countDown();
      assertThat(List.of(publish.get(10, TimeUnit.SECONDS), stop.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    var rows =
        jdbc.sql("SELECT kind, on_sale FROM catalog_product")
            .query((row, index) -> Map.entry(row.getString(1), row.getBoolean(2)))
            .list();
    boolean dishSale =
        rows.stream()
            .filter(row -> row.getKey().equals("DISH"))
            .findFirst()
            .orElseThrow()
            .getValue();
    boolean mealSale =
        rows.stream()
            .filter(row -> row.getKey().equals("SET_MEAL"))
            .findFirst()
            .orElseThrow()
            .getValue();
    assertThat(!mealSale || dishSale).isTrue();
  }

  private byte[] png() throws Exception {
    var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(image, "png", bytes);
    return bytes.toByteArray();
  }

  private long objectCount() {
    return storage
        .listObjectsV2Paginator(request -> request.bucket(bucket).prefix(objectPrefix))
        .contents()
        .stream()
        .count();
  }

  private String bearer() {
    return "Bearer " + token;
  }

  private JsonNode body(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  private ResultActions createCategory(String kind, String name) throws Exception {
    return mvc.perform(
        post("/api/v1/catalog/categories")
            .header("Authorization", bearer())
            .contentType("application/json")
            .content(json.writeValueAsString(Map.of("kind", kind, "name", name, "sortOrder", 0))));
  }

  private String category(String kind, String name) throws Exception {
    return body(createCategory(kind, name).andExpect(status().isCreated()).andReturn())
        .path("id")
        .asString();
  }

  private ResultActions updateCategory(String id, boolean enabled, long version) throws Exception {
    return mvc.perform(
        put("/api/v1/catalog/categories/{id}", id)
            .header("Authorization", bearer())
            .contentType("application/json")
            .content(
                json.writeValueAsString(
                    Map.of("name", "饮品", "sortOrder", 0, "enabled", enabled, "version", version))));
  }

  private Map<String, Object> details(
      String category, String name, List<?> flavors, List<?> components, String imageId) {
    var map = new HashMap<String, Object>();
    map.put("categoryId", category);
    map.put("name", name);
    map.put("description", "");
    map.put("price", "18.50");
    map.put("flavors", flavors);
    map.put("components", components);
    map.put("imageId", imageId);
    return map;
  }

  private ResultActions createProduct(String kind, Map<String, Object> details) throws Exception {
    return mvc.perform(
        post("/api/v1/catalog/products")
            .header("Authorization", bearer())
            .contentType("application/json")
            .content(json.writeValueAsString(Map.of("kind", kind, "details", details))));
  }

  private String product(String kind, Map<String, Object> details) throws Exception {
    return body(createProduct(kind, details).andExpect(status().isCreated()).andReturn())
        .path("id")
        .asString();
  }

  private ResultActions sale(String id, String status, long version) throws Exception {
    return mvc.perform(
        patch("/api/v1/catalog/products/{id}/status", id)
            .header("Authorization", bearer())
            .contentType("application/json")
            .content(json.writeValueAsString(Map.of("status", status, "version", version))));
  }

  private ResultActions publicMenu() throws Exception {
    return mvc.perform(get("/api/v1/menu/products"));
  }

  private List<String> cacheKeys() {
    var keys = new ArrayList<String>();
    try (var cursor =
        redis.scan(ScanOptions.scanOptions().match(cachePrefix + "*").count(100).build())) {
      cursor.forEachRemaining(keys::add);
    }
    return keys;
  }

  private void clearCache() {
    var keys = cacheKeys();
    if (!keys.isEmpty()) {
      redis.delete(keys);
    }
  }
}
