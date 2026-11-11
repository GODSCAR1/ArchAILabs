package com.codeja.master_acdc.exports;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ExportsArchitectureTest {
  @Test
  void should_keep_exports_independent_when_checking_module_dependencies() {
    var classes =
        new ClassFileImporter()
            .withImportOption(
                new com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests())
            .importPackages("com.codeja.master_acdc");
    noClasses()
        .that()
        .resideInAPackage("..exports..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..catalog..", "..identity..", "..ordering..", "..inventory..")
        .check(classes);
    noClasses()
        .that()
        .resideInAnyPackage("..exports.domain..", "..exports.application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..", "jakarta.persistence..", "..exports.infrastructure..")
        .check(classes);
  }
}
