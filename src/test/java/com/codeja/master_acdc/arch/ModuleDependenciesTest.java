package com.codeja.master_acdc.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.codeja.master_acdc",
    importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ModuleDependenciesTest {
  @ArchTest
  static final ArchRule modules_have_no_cycles =
      slices().matching("com.codeja.master_acdc.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule identity_uses_only_public_ordering_api =
      noClasses()
          .that()
          .resideInAPackage("com.codeja.master_acdc.identity..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.codeja.master_acdc.ordering.domain..",
              "com.codeja.master_acdc.ordering.application..",
              "com.codeja.master_acdc.ordering.infrastructure..");

  @ArchTest
  static final ArchRule ordering_and_inventory_do_not_depend_on_identity =
      noClasses()
          .that()
          .resideInAnyPackage(
              "com.codeja.master_acdc.ordering..", "com.codeja.master_acdc.inventory..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.codeja.master_acdc.identity..");

  @ArchTest
  static final ArchRule only_identity_infrastructure_bridges_to_ordering =
      noClasses()
          .that()
          .resideInAPackage("com.codeja.master_acdc.identity..")
          .and()
          .resideOutsideOfPackage("com.codeja.master_acdc.identity.infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.codeja.master_acdc.ordering..");

  @ArchTest
  static final ArchRule ordering_uses_only_public_inventory_api =
      freeze(
          noClasses()
              .that()
              .resideInAPackage("com.codeja.master_acdc.ordering..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(
                  "com.codeja.master_acdc.inventory.domain..",
                  "com.codeja.master_acdc.inventory.application..",
                  "com.codeja.master_acdc.inventory.infrastructure.."));

  @ArchTest
  static final ArchRule inventory_does_not_depend_on_ordering =
      noClasses()
          .that()
          .resideInAPackage("com.codeja.master_acdc.inventory..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.codeja.master_acdc.ordering..");
}
