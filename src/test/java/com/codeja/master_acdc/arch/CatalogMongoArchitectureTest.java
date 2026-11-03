package com.codeja.master_acdc.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.codeja.master_acdc.catalog.application.port.ProductDetailUseCase;
import com.codeja.master_acdc.catalog.domain.model.ProductDetail;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.cache.annotation.Cacheable;

@AnalyzeClasses(
    packages = "com.codeja.master_acdc",
    importOptions = ImportOption.DoNotIncludeTests.class)
class CatalogMongoArchitectureTest {

  @ArchTest
  static final ArchRule mongo_dependencies_stay_in_catalog_infrastructure =
      noClasses()
          .that()
          .resideOutsideOfPackage("com.codeja.master_acdc.catalog.infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework.data.mongodb..", "org.bson..");

  @ArchTest
  static final ArchRule catalog_has_no_jakarta_persistence_dependencies =
      noClasses()
          .that()
          .resideInAPackage("com.codeja.master_acdc.catalog..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("jakarta.persistence..");

  @ArchTest
  static final ArchRule catalog_has_no_spring_data_jpa_dependencies =
      noClasses()
          .that()
          .resideInAPackage("com.codeja.master_acdc.catalog..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework.data.jpa..");

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
  static final ArchRule catalog_domain_has_no_framework_or_cache_dependencies =
      noClasses()
          .that()
          .resideInAPackage("com.codeja.master_acdc.catalog.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "org.springframework.data.redis..",
              "com.github.benmanes.caffeine..",
              "tools.jackson..",
              "com.fasterxml.jackson..");

  @ArchTest
  static final ArchRule checkout_modules_do_not_depend_on_cached_product_detail =
      noClasses()
          .that()
          .resideInAnyPackage(
              "com.codeja.master_acdc.ordering..", "com.codeja.master_acdc.inventory..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.codeja.master_acdc.catalog.infrastructure.adapters.out.cache..");

  @ArchTest
  static final ArchRule checkout_modules_do_not_depend_on_product_detail_use_case =
      noClasses()
          .that()
          .resideInAnyPackage(
              "com.codeja.master_acdc.ordering..", "com.codeja.master_acdc.inventory..")
          .should()
          .dependOnClassesThat()
          .areAssignableTo(ProductDetailUseCase.class);

  @ArchTest
  static final ArchRule cacheable_methods_only_return_immutable_product_details =
      methods()
          .that()
          .areAnnotatedWith(Cacheable.class)
          .should()
          .haveRawReturnType(ProductDetail.class);

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
