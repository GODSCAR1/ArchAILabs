package com.codeja.master_acdc.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.codeja.master_acdc.IntegrationTest;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

@AnalyzeClasses(
    packages = "com.codeja.master_acdc",
    importOptions = ImportOption.OnlyIncludeTests.class)
class IntegrationTestRulesTest {
  @ArchTest
  static final ArchRule only_integration_base_declares_spring_boot_test =
      noClasses()
          .that()
          .doNotHaveFullyQualifiedName(IntegrationTest.class.getName())
          .should()
          .beAnnotatedWith(SpringBootTest.class)
          .because("un único contexto comparte los mismos contenedores");

  @ArchTest
  static final ArchRule concrete_integration_tests_use_failsafe_names =
      classes()
          .that()
          .areAssignableTo(IntegrationTest.class)
          .and()
          .doNotHaveModifier(JavaModifier.ABSTRACT)
          .should()
          .haveSimpleNameEndingWith("IT")
          .because("Surefire ejecuta unitarios y Failsafe los tests de integración");

  @ArchTest
  static final ArchRule tests_do_not_depend_on_h2 =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.h2..")
          .because("los tests usan la misma base de datos que producción");

  @ArchTest
  static final ArchRule integration_tests_do_not_declare_context_overrides =
      classes()
          .that()
          .areAssignableTo(IntegrationTest.class)
          .and()
          .doNotHaveFullyQualifiedName(IntegrationTest.class.getName())
          .should(noContextOverrides())
          .because("un único contexto comparte los mismos contenedores");

  private static ArchCondition<JavaClass> noContextOverrides() {
    return new ArchCondition<>("not declare context overrides") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        if (item.isAnnotatedWith(MockitoBean.class)
            || item.isAnnotatedWith(TestPropertySource.class)
            || item.isAnnotatedWith(DirtiesContext.class)
            || item.isAnnotatedWith(Testcontainers.class)
            || item.getFields().stream()
                .anyMatch(field -> field.isAnnotatedWith(MockitoBean.class))) {
          events.add(
              new SimpleConditionEvent(
                  item, false, item.getName() + " declares a context override"));
        }
      }
    };
  }
}
