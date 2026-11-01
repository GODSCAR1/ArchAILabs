package com.codeja.master_acdc.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Firmas del dominio con tipos explícitos (lab S09, prueba 1): un constructor o método que recibe
 * {@code Object} u {@code Object[]} (incluido {@code Object...}) pierde la seguridad de tipos y
 * suele ser un rodeo para no cambiar una firma que usan otros (por ejemplo, un test guardián).
 */
@AnalyzeClasses(
    packages = "com.codeja.master_acdc",
    importOptions = ImportOption.DoNotIncludeTests.class)
class DomainSignatureRulesTest {

  @ArchTest
  static final ArchRule domain_code_units_do_not_accept_object_parameters =
      classes().that().resideInAPackage("..domain..").should(notAcceptObjectParameters());

  private static ArchCondition<JavaClass> notAcceptObjectParameters() {
    return new ArchCondition<>("not accept Object or Object[] parameters") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        item.getCodeUnits().stream()
            .filter(codeUnit -> !"equals".equals(codeUnit.getName()))
            .filter(DomainSignatureRulesTest::acceptsObject)
            .forEach(
                codeUnit ->
                    events.add(
                        new SimpleConditionEvent(
                            item, false, codeUnit.getFullName() + " accepts Object or Object[]")));
      }
    };
  }

  private static boolean acceptsObject(JavaCodeUnit codeUnit) {
    return codeUnit.getRawParameterTypes().stream()
        .anyMatch(
            type ->
                type.isEquivalentTo(Object.class)
                    || (type.isArray() && type.getComponentType().isEquivalentTo(Object.class)));
  }
}
