package com.codeja.master_acdc.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.codeja.master_acdc",
    importOptions = ImportOption.DoNotIncludeTests.class)
class CatalogArchitectureTest {

  @ArchTest
  static final ArchRule mongo_dependencies_stay_in_catalog_infrastructure =
      noClasses()
          .that()
          .resideOutsideOfPackage("com.codeja.master_acdc.catalog.infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework.data.mongodb..", "org.bson..");

  @ArchTest
  static final ArchRule catalog_application_depends_only_on_domain =
      classes()
          .that()
          .resideInAPackage("com.codeja.master_acdc.catalog.application..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              "java..",
              "com.codeja.master_acdc.catalog.application..",
              "com.codeja.master_acdc.catalog.domain..");

  @ArchTest
  static final ArchRule catalog_does_not_depend_on_inventory =
      noClasses()
          .that()
          .resideInAPackage("com.codeja.master_acdc.catalog..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.codeja.master_acdc.inventory..");

  @ArchTest
  static final ArchRule catalog_mappers_stay_in_adapter_packages =
      classes()
          .that()
          .resideInAPackage("com.codeja.master_acdc.catalog..")
          .and()
          .haveSimpleNameEndingWith("Mapper")
          .should()
          .resideInAPackage("com.codeja.master_acdc.catalog.infrastructure.adapters..");
}
