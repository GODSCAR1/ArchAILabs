package com.codeja.master_acdc.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

class AuthorizationArchitectureTest {
  private static final JavaClasses PRODUCTION =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages("com.codeja.master_acdc");

  @Test
  void should_keep_frameworks_and_infrastructure_out_when_importing_pure_layers() {
    noClasses()
        .that()
        .resideInAnyPackage("..domain..", "..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..infrastructure..",
            "org.springframework..",
            "jakarta.persistence..",
            "com.fasterxml.jackson..",
            "tools.jackson..")
        .check(PRODUCTION);
  }

  @Test
  void should_not_accept_customer_identity_when_order_http_methods_are_inspected() {
    var controllers =
        PRODUCTION.stream()
            .filter(
                type -> type.getPackageName().contains(".ordering.infrastructure.adapters.in.web"))
            .filter(type -> type.isAnnotatedWith(RestController.class))
            .toList();
    assertThat(controllers).isNotEmpty();
    for (var controller : controllers) {
      assertController(controller.reflect());
    }
    PRODUCTION.stream()
        .filter(type -> type.getPackageName().contains(".ordering.infrastructure.adapters.in.web"))
        .filter(
            type ->
                type.getSimpleName().endsWith("Request")
                    || type.getSimpleName().endsWith("Response"))
        .forEach(
            type ->
                assertThat(type.getAllFields())
                    .noneMatch(field -> field.getName().equalsIgnoreCase("customerId")));
  }

  private void assertController(Class<?> controller) {
    for (var method : controller.getDeclaredMethods()) {
      for (var parameter : method.getParameters()) {
        assertThat(parameter.getAnnotation(RequestHeader.class)).isNull();
        assertThat(parameter.getName()).doesNotContain("customerId");
      }
    }
  }

  static Stream<Arguments> commands() {
    return Map.of(
            "catalog", Map.of("CreateProduct", "product:write", "EditProduct", "product:write"),
            "inventory", Map.of("ReserveProductStock", "stock:reserve"),
            "ordering",
                Map.of(
                    "CreateOrder",
                    "order:create",
                    "CancelOrder",
                    "order:cancel-own",
                    "RefundOrder",
                    "order:refund"))
        .entrySet()
        .stream()
        .flatMap(
            module ->
                module.getValue().entrySet().stream()
                    .map(
                        command ->
                            Arguments.of(module.getKey(), command.getKey(), command.getValue())));
  }

  @ParameterizedTest(name = "{1}UseCase exige {2}")
  @MethodSource("commands")
  void should_decorate_proxifiable_business_commands_when_http_boundary_is_inspected(
      String module, String command, String permission) {
    String base = "com.codeja.master_acdc." + module;
    var decorator =
        PRODUCTION
            .get(base + ".infrastructure.adapters.in.web.Authorized" + command + "UseCase")
            .reflect();
    var contract = PRODUCTION.get(base + ".application.port." + command + "UseCase").reflect();
    assertThat(contract.isAssignableFrom(decorator)).isTrue();
    assertThat(Modifier.isPublic(decorator.getModifiers())).isTrue();
    assertThat(Modifier.isFinal(decorator.getModifiers())).isFalse();
    var methods =
        Arrays.stream(decorator.getDeclaredMethods())
            .filter(method -> !method.isSynthetic())
            .toList();
    assertThat(methods).isNotEmpty();
    methods.forEach(
        method -> {
          assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
          assertThat(Modifier.isFinal(method.getModifiers())).isFalse();
          assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull();
          assertThat(method.getAnnotation(PreAuthorize.class).value())
              .isEqualTo("hasAuthority('" + permission + "')");
        });
  }

  @Test
  void should_restrict_application_to_own_domain_when_modules_are_imported() {
    for (String module : java.util.List.of("catalog", "inventory", "ordering", "identity")) {
      String base = "com.codeja.master_acdc." + module;
      classes()
          .that()
          .resideInAPackage(base + ".application..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", base + ".application..", base + ".domain..")
          .check(PRODUCTION);
    }
    freeze(
            noClasses()
                .that()
                .resideInAPackage("..api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "tools.jackson..",
                    "com.fasterxml.jackson.."))
        .check(PRODUCTION);
    classes()
        .that()
        .resideInAPackage("..domain.port..")
        .and()
        .haveSimpleNameNotEndingWith("package-info")
        .should()
        .beInterfaces()
        .check(PRODUCTION);
  }

  @Test
  void should_keep_mappers_static_and_in_infrastructure_when_boundaries_are_inspected() {
    var mappers =
        PRODUCTION.stream().filter(type -> type.getSimpleName().endsWith("Mapper")).toList();
    assertThat(mappers).isNotEmpty();
    mappers.forEach(
        type -> {
          assertThat(type.getPackageName()).contains(".infrastructure.");
          assertThat(Modifier.isFinal(type.reflect().getModifiers())).isTrue();
          assertThat(Arrays.stream(type.reflect().getDeclaredFields()))
              .allMatch(field -> Modifier.isStatic(field.getModifiers()));
          assertThat(Arrays.stream(type.reflect().getDeclaredMethods()))
              .allMatch(method -> Modifier.isStatic(method.getModifiers()));
        });
  }
}
