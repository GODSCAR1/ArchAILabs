package com.codeja.master_acdc;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.codeja.master_acdc")
class CatalogArchitectureTest {

  @ArchTest
  static final ArchRule catalogDoesNotDependOnInventory =
      noClasses()
          .that()
          .resideInAnyPackage("..catalog..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..inventory..");

  @ArchTest
  static final ArchRule inventoryDoesNotDependOnCatalog =
      noClasses()
          .that()
          .resideInAnyPackage("..inventory..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..catalog..");
}
