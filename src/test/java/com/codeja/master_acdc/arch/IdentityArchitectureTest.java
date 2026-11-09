package com.codeja.master_acdc.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.reflect.Modifier;

@AnalyzeClasses(
    packages = "com.codeja.master_acdc",
    importOptions = ImportOption.DoNotIncludeTests.class)
class IdentityArchitectureTest {
  @ArchTest
  static final ArchRule domain_is_pure =
      classes()
          .that()
          .resideInAPackage("..identity.domain..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", "..identity.domain..");

  @ArchTest
  static final ArchRule application_is_pure =
      classes()
          .that()
          .resideInAPackage("..identity.application..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", "..identity.application..", "..identity.domain..");

  @ArchTest
  static final ArchRule output_ports_are_interfaces =
      classes()
          .that()
          .resideInAPackage("..identity.domain.port")
          .and()
          .haveSimpleNameNotEndingWith("package-info")
          .should()
          .beInterfaces();

  @ArchTest
  static final ArchRule no_private_module_dependencies =
      noClasses()
          .that()
          .resideInAPackage("..identity..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..ordering.domain..",
              "..ordering.application..",
              "..ordering.infrastructure..",
              "..inventory..",
              "..catalog..");

  @ArchTest
  static final ArchRule no_reverse_dependency =
      noClasses()
          .that()
          .resideInAnyPackage(
              "com.codeja.master_acdc.ordering..", "com.codeja.master_acdc.inventory..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..identity..");

  @ArchTest
  static final ArchRule ordering_api_is_pure =
      classes()
          .that()
          .resideInAPackage("..ordering.api..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", "..ordering.api..");

  @ArchTest
  static final ArchRule security_stays_in_infrastructure =
      noClasses()
          .that()
          .resideOutsideOfPackage("..identity.infrastructure..")
          .and()
          .resideInAPackage("..identity..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework.security..", "com.nimbusds..");

  @ArchTest
  static final ArchRule mappers_are_stateless =
      classes()
          .that()
          .resideInAPackage("..identity..")
          .and()
          .haveSimpleNameEndingWith("Mapper")
          .should()
          .haveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
          .andShould()
          .resideInAPackage("..infrastructure..")
          .andShould(
              new ArchCondition<JavaClass>("have only static fields and methods") {
                @Override
                public void check(JavaClass type, ConditionEvents events) {
                  boolean stateless =
                      type.reflect().getDeclaredFields().length == 0
                          && java.util.Arrays.stream(type.reflect().getDeclaredMethods())
                              .allMatch(m -> Modifier.isStatic(m.getModifiers()));
                  events.add(
                      new SimpleConditionEvent(
                          type, stateless, type.getName() + " must be stateless"));
                }
              });

  @ArchTest
  static final ArchRule entities_do_not_convert =
      noClasses()
          .that()
          .resideInAPackage("..identity.infrastructure.adapters.out.persistence..")
          .and()
          .haveSimpleNameEndingWith("Entity")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..identity.domain..");
}
