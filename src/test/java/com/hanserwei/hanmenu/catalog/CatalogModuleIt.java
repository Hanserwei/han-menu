package com.hanserwei.hanmenu.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanserwei.hanmenu.catalog.application.CatalogService;
import com.hanserwei.hanmenu.catalog.events.DishPublished;
import com.hanserwei.hanmenu.support.TestDatabase;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;

@ApplicationModuleTest
@ActiveProfiles("test")
@DirtiesContext
@TestExecutionListeners(
    listeners = TestDatabase.Cleanup.class,
    mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
class CatalogModuleIt {
  private static final TestDatabase DATABASE = new TestDatabase(CatalogModuleIt.class);

  @Autowired private CatalogService catalog;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    DATABASE.configure(registry);
  }

  @Test
  void publishesItsContractWithoutLoadingTheMenuModule(Scenario scenario) {
    var id = catalog.create("Module test dish", BigDecimal.TEN);
    scenario
        .stimulate(() -> catalog.publish(id))
        .andWaitForEventOfType(DishPublished.class)
        .matching(event -> event.dishId().equals(id))
        .toArriveAndVerify(event -> assertThat(event.price()).isEqualByComparingTo("10.00"));
  }
}
