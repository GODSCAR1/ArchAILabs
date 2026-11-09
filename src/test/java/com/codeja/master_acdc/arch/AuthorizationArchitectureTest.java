package com.codeja.master_acdc.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.ordering.infrastructure.adapters.in.web.OrderController;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class AuthorizationArchitectureTest {
  @Test
  void should_keep_frameworks_and_infrastructure_out_when_importing_pure_layers() {
    noClasses()
        .that()
        .resideInAnyPackage("..domain..", "..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure..", "org.springframework..")
        .check(
            new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.codeja.master_acdc"));
  }

  @Test
  void should_not_accept_customer_identity_when_order_http_methods_are_inspected() {
    for (var method : OrderController.class.getDeclaredMethods()) {
      for (var parameter : method.getParameters()) {
        assertThat(
                parameter.getAnnotation(
                    org.springframework.web.bind.annotation.RequestHeader.class))
            .isNull();
        assertThat(parameter.getName()).doesNotContain("customerId");
      }
    }
  }
}
