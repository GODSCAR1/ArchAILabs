package com.codeja.master_acdc.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Map;
import java.util.Optional;

/**
 * Fronteras entre módulos (lab S18). El mapa de relaciones permitidas está en docs/context-map.md:
 * si una regla falla por una dependencia que no está en el mapa, o el código o el mapa están mal.
 */
@AnalyzeClasses(
    packages = "com.codeja.master_acdc",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

  static final String ROOT = "com.codeja.master_acdc.";

  /**
   * Paquete publicado de cada módulo. Los que no aparecen (identity, exports) no publican nada.
   * pricing es un núcleo compartido sin fachada: su modelo de dominio es su lenguaje publicado.
   */
  static final Map<String, String> PUBLISHED =
      Map.of(
          "catalog", "catalog.api",
          "inventory", "inventory.api",
          "ordering", "ordering.api",
          "pricing", "pricing.domain.model");

  @ArchTest
  static final ArchRule modules_are_free_of_cycles =
      slices()
          .matching("com.codeja.master_acdc.(*)..")
          .should()
          .beFreeOfCycles()
          .because(
              "un ciclo entre módulos los convierte en uno solo: invierte la dependencia con un"
                  + " puerto en el módulo que la necesita, o publica un evento de dominio en lugar"
                  + " de llamar de vuelta (docs/context-map.md)");

  @ArchTest
  static final ArchRule modules_only_use_the_published_api_of_other_modules =
      freeze(
          classes()
              .that()
              .resideInAPackage("com.codeja.master_acdc.*..")
              .should(onlyUseThePublishedApiOfOtherModulesFromInfrastructure())
              .because(
                  "un módulo solo ve de otro su paquete publicado (api; en pricing, domain.model) y"
                      + " solo desde infrastructure: el núcleo pide lo que necesita a un puerto"
                      + " propio de domain/port y un adaptador de"
                      + " infrastructure/adapters/out/<modulo> llama a la api del otro"
                      + " (docs/context-map.md)"));

  private static ArchCondition<JavaClass> onlyUseThePublishedApiOfOtherModulesFromInfrastructure() {
    return new ArchCondition<>(
        "only use the published api of other modules, and only from infrastructure") {
      @Override
      public void check(JavaClass origin, ConditionEvents events) {
        Optional<String> originModule = moduleOf(origin);
        if (originModule.isEmpty()) {
          return;
        }
        for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
          violation(origin, originModule.get(), dependency)
              .ifPresent(
                  reason ->
                      events.add(
                          SimpleConditionEvent.violated(
                              dependency, dependency.getDescription() + reason)));
        }
      }
    };
  }

  /** Motivo de la violación, o vacío si la dependencia respeta la frontera. */
  static Optional<String> violation(JavaClass origin, String originModule, Dependency dependency) {
    JavaClass target = dependency.getTargetClass();
    Optional<String> targetModule = moduleOf(target);
    if (targetModule.isEmpty() || targetModule.get().equals(originModule)) {
      return Optional.empty();
    }
    String published = PUBLISHED.get(targetModule.get());
    if (published == null || !target.getPackageName().startsWith(ROOT + published)) {
      return Optional.of(" [" + targetModule.get() + " no publica ese paquete]");
    }
    if (!origin.getPackageName().startsWith(ROOT + originModule + ".infrastructure")) {
      return Optional.of(" [solo infrastructure puede usar otro módulo]");
    }
    return Optional.empty();
  }

  static Optional<String> moduleOf(JavaClass type) {
    String name = type.getPackageName();
    if (!name.startsWith(ROOT)) {
      return Optional.empty();
    }
    String rest = name.substring(ROOT.length());
    int dot = rest.indexOf('.');
    return Optional.of(dot < 0 ? rest : rest.substring(0, dot));
  }
}
