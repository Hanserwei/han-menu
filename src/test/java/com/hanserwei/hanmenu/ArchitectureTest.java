package com.hanserwei.hanmenu;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ArchitectureTest {
  @Test
  void modulesRespectExportsAndHaveNoCycles() {
    var modules = ApplicationModules.of(HanMenuApplication.class).verify();
    new Documenter(modules).writeDocumentation();
  }

  @Test
  void layersPointInwardAndDomainRemainsFrameworkFree() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.hanserwei.hanmenu");
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideOutsideOfPackages("java..", "com.hanserwei.hanmenu..domain..")
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
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..web..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure..", "org.springframework.jdbc..")
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..infrastructure..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..application..", "..web..")
        .check(classes);
  }
}
