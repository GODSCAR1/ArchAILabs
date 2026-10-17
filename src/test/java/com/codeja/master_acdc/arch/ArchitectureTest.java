package com.codeja.master_acdc.arch;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.context.ApplicationContext;

/** Verifica las reglas de arquitectura hexagonal de {@code design.md}. */
@AnalyzeClasses(
    packages = "com.codeja.master_acdc",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String[] FRAMEWORKS = {
    "org.springframework..", "jakarta.persistence..", "com.fasterxml.jackson.."
  };

  @ArchTest
  static final ArchRule inventory_domain_has_no_framework_dependencies =
      noClasses()
          .that()
          .resideInAPackage("com.codeja.master_acdc.inventory.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORKS);

  @ArchTest
  static final ArchRule ordering_domain_has_no_framework_dependencies =
      noClasses()
          .that()
          .resideInAPackage("com.codeja.master_acdc.ordering.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORKS);

  @ArchTest
  static final ArchRule inventory_application_depends_only_on_domain =
      classes()
          .that()
          .resideInAPackage("com.codeja.master_acdc.inventory.application..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              "java..",
              "com.codeja.master_acdc.inventory.application..",
              "com.codeja.master_acdc.inventory.domain..");

  @ArchTest
  static final ArchRule ordering_application_depends_only_on_domain =
      classes()
          .that()
          .resideInAPackage("com.codeja.master_acdc.ordering.application..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              "java..",
              "com.codeja.master_acdc.ordering.application..",
              "com.codeja.master_acdc.ordering.domain..");

  @ArchTest
  static final ArchRule inventory_api_is_only_used_by_ordering_infrastructure =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "com.codeja.master_acdc.ordering.infrastructure..",
              "com.codeja.master_acdc.inventory..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.codeja.master_acdc.inventory.api..");

  @ArchTest
  static final ArchRule inventory_does_not_depend_on_ordering =
      noClasses()
          .that()
          .resideInAPackage("com.codeja.master_acdc.inventory..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.codeja.master_acdc.ordering..");

  @ArchTest
  static final ArchRule no_application_context_get_bean_outside_config =
      noClasses()
          .that()
          .resideOutsideOfPackage("..config..")
          .should()
          .callMethodWhere(
              target(owner(assignableTo(ApplicationContext.class))).and(target(name("getBean"))));
}
