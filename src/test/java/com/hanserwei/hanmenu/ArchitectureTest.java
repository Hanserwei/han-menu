package com.hanserwei.hanmenu;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** 验证业务模块边界和依赖方向，防止后续开发退化为跨模块调用内部实现的结构. */
class ArchitectureTest {
  /** 校验模块清单、公开契约及循环依赖，并从真实代码结构生成模块文档. */
  @Test
  void modulesRespectExportsAndHaveNoCycles() {
    var modules = ApplicationModules.of(HanMenuApplication.class).verify();
    assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
        .containsExactlyInAnyOrder(
            "identity",
            "customer",
            "shop",
            "catalog",
            "cart",
            "ordering",
            "payment",
            "notification",
            "reporting");
    new Documenter(modules).writeDocumentation();
  }

  /** 领域层保持纯 Java，应用层和适配器遵守向内依赖的规则. */
  @Test
  void layersPointInwardAndDomainRemainsFrameworkFree() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hanserwei.hanmenu");
    // 骨架阶段允许分层包中尚无实现类；一旦加入实现，下列依赖约束立即适用。
    // 此处只允许空的匹配集合，并未豁免任何已有类的依赖检查。
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideOutsideOfPackages("java..", "com.hanserwei.hanmenu..domain..")
        .allowEmptyShould(true)
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..infrastructure..",
            "..web..",
            "org.springframework.jdbc..",
            "java.sql..",
            "javax.sql..")
        .allowEmptyShould(true)
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..web..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure..", "org.springframework.jdbc..")
        .allowEmptyShould(true)
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..infrastructure..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..application..", "..web..")
        .allowEmptyShould(true)
        .check(classes);
  }
}
