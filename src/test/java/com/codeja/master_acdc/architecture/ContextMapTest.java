package com.codeja.master_acdc.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;
import java.util.Set;

/**
 * Dependencias prohibidas del mapa de contextos (lab S19). Fuente: docs/domain/context-map.md y los
 * ADR 0013 (límites) y 0014 (integración). Amplía ModuleBoundariesTest (lab S18): aquel exige usar
 * solo el paquete publicado; este fija la dirección de cada relación y la lista cerrada de puertos
 * entre contextos.
 */
@AnalyzeClasses(
    packages = "com.codeja.master_acdc",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ContextMapTest {

  static final String ROOT = "com.codeja.master_acdc.";
  static final String MAP = " (docs/domain/context-map.md)";

  private static String[] modules(String... names) {
    String[] packages = new String[names.length];
    for (int i = 0; i < names.length; i++) {
      packages[i] = ROOT + names[i] + "..";
    }
    return packages;
  }

  @ArchTest
  static final ArchRule catalog_depends_on_no_other_context =
      noClasses()
          .that()
          .resideInAPackage(ROOT + "catalog..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(modules("ordering", "inventory", "pricing", "identity", "exports"))
          .because(
              "Catálogo es upstream de Pedidos: si necesita algo de otro contexto, reacciona a un"
                  + " evento de ese contexto (ADR 0014)"
                  + MAP);

  @ArchTest
  static final ArchRule inventory_depends_on_no_other_context =
      noClasses()
          .that()
          .resideInAPackage(ROOT + "inventory..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(modules("ordering", "catalog", "pricing", "identity", "exports"))
          .because(
              "Inventario es upstream de Pedidos y de Catálogo: no conoce a sus consumidores (ADR"
                  + " 0013)"
                  + MAP);

  @ArchTest
  static final ArchRule pricing_depends_on_no_other_context =
      noClasses()
          .that()
          .resideInAPackage(ROOT + "pricing..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(modules("ordering", "catalog", "inventory", "identity", "exports"))
          .because(
              "Precios es el dueño del shared kernel: si dependiera de Pedidos, el kernel tendría"
                  + " un ciclo (ADR 0013)"
                  + MAP);

  @ArchTest
  static final ArchRule identity_only_depends_on_the_ordering_open_host =
      noClasses()
          .that()
          .resideInAPackage(ROOT + "identity..")
          .should()
          .dependOnClassesThat(
              resideInAnyPackage(modules("catalog", "inventory", "pricing", "exports"))
                  .or(
                      resideInAPackage(ROOT + "ordering..")
                          .and(not(resideInAPackage(ROOT + "ordering.api..")))))
          .because(
              "Identidad solo implementa el open host service de Pedidos (ordering.api); Pedidos no"
                  + " depende de Identidad (ADR 0013)"
                  + MAP);

  @ArchTest
  static final ArchRule exports_is_isolated =
      classes()
          .that()
          .resideInAPackage(ROOT + "exports..")
          .should()
          .onlyDependOnClassesThat(
              not(
                  resideInAnyPackage(
                      modules("catalog", "inventory", "ordering", "pricing", "identity"))))
          .andShould()
          .onlyHaveDependentClassesThat()
          .resideInAPackage(ROOT + "exports..")
          .because("exports no es un contexto sino un detalle técnico (ADR 0013)" + MAP);

  /** Shared kernel de ADR 0013 §3: lo único de pricing que otro contexto puede ver. */
  static final Set<String> SHARED_KERNEL =
      Set.of("Money", "OrderPricing", "PricingOrder", "PricingLine");

  static final String SHARED_KERNEL_PACKAGE = ROOT + "pricing.domain.model";

  @ArchTest
  static final ArchRule shared_kernel_is_limited =
      noClasses()
          .that()
          .resideOutsideOfPackage(ROOT + "pricing..")
          .should()
          .dependOnClassesThat(
              resideInAPackage(ROOT + "pricing..")
                  .and(
                      DescribedPredicate.describe(
                          "are not in the shared kernel " + SHARED_KERNEL,
                          type ->
                              !SHARED_KERNEL_PACKAGE.equals(type.getPackageName())
                                  || !SHARED_KERNEL.contains(type.getSimpleName()))))
          .because(
              "el shared kernel es solo Money, OrderPricing, PricingOrder y PricingLine; ampliarlo"
                  + " es una decisión que exige un ADR nuevo (ADR 0013 §3)"
                  + MAP);

  /**
   * Lista cerrada de tipos de otro contexto que se pueden usar (ADR 0014 §1 y §4). Un puerto nuevo
   * entre contextos se añade aquí, en el mapa de contextos y, si es un efecto síncrono, en un ADR.
   */
  static final Set<String> CATALOGUED_PORTS =
      Set.of(
          // Consultas síncronas
          ROOT + "catalog.api.CatalogPrices",
          ROOT + "catalog.api.CatalogPrices$ProductPrice",
          // Open host service de Pedidos que implementa Identidad
          ROOT + "ordering.api.CurrentOrderActor",
          ROOT + "ordering.api.CurrentOrderActor$Actor",
          ROOT + "ordering.api.CustomerRegistrationFacade",
          // Excepción ADR 0015: la reserva es un efecto síncrono porque un pedido no nace sin stock
          ROOT + "inventory.api.InventoryFacade",
          // Deuda lab S12 (congelada en ModuleBoundariesTest): InventoryFacade publica tipos de su
          // dominio
          ROOT + "inventory.domain.model.StockLine",
          ROOT + "inventory.domain.model.ReservationResult",
          // Deuda ADR 0014 §4: Pedidos empuja la disponibilidad al catálogo; pasa a evento en la
          // Sección 39
          ROOT + "catalog.api.CatalogStockProjection",
          // Shared kernel (ADR 0013 §3)
          ROOT + "pricing.domain.model.Money",
          ROOT + "pricing.domain.model.OrderPricing",
          ROOT + "pricing.domain.model.PricingOrder",
          ROOT + "pricing.domain.model.PricingLine");

  @ArchTest
  static final ArchRule cross_context_calls_only_use_catalogued_ports =
      classes()
          .that()
          .resideInAPackage(ROOT + "*..")
          .should(onlyCrossContextsThrough(CATALOGUED_PORTS))
          .because(
              "entre contextos solo hay consultas por puertos publicados y efectos por eventos de"
                  + " dominio; la lista cerrada está en ContextMapTest.CATALOGUED_PORTS y cada"
                  + " puerto nuevo exige cambiar el mapa (ADR 0014)"
                  + MAP);

  @ArchTest
  static final ArchRule no_module_touches_another_modules_persistence =
      classes()
          .that()
          .resideInAPackage(ROOT + "*..")
          .should(notTouchPersistenceOfOtherModules())
          .because(
              "las tablas, colecciones, entidades y repositorios son del contexto que los crea;"
                  + " otro contexto pide los datos por su puerto publicado o reacciona a sus"
                  + " eventos (ADR 0014 §3)"
                  + MAP);

  private static ArchCondition<JavaClass> onlyCrossContextsThrough(Set<String> allowed) {
    return new ArchCondition<>("only use catalogued ports of other contexts") {
      @Override
      public void check(JavaClass origin, ConditionEvents events) {
        Optional<String> from = moduleOf(origin);
        for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
          JavaClass target = dependency.getTargetClass().getBaseComponentType();
          Optional<String> to = moduleOf(target);
          if (from.isPresent()
              && to.isPresent()
              && !from.equals(to)
              && !allowed.contains(target.getName())) {
            events.add(
                SimpleConditionEvent.violated(
                    dependency,
                    dependency.getDescription()
                        + " ["
                        + target.getName()
                        + " no es un puerto catalogado]"));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notTouchPersistenceOfOtherModules() {
    return new ArchCondition<>("not touch the persistence of other modules") {
      @Override
      public void check(JavaClass origin, ConditionEvents events) {
        Optional<String> from = moduleOf(origin);
        for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
          JavaClass target = dependency.getTargetClass().getBaseComponentType();
          Optional<String> to = moduleOf(target);
          if (from.isPresent() && to.isPresent() && !from.equals(to) && isPersistence(target)) {
            events.add(
                SimpleConditionEvent.violated(
                    dependency,
                    dependency.getDescription() + " [persistencia de " + to.get() + "]"));
          }
        }
      }
    };
  }

  static boolean isPersistence(JavaClass type) {
    String pkg = type.getPackageName();
    return pkg.contains(".adapters.out.persistence")
        || pkg.contains(".adapters.out.mongo")
        || type.isAnnotatedWith("jakarta.persistence.Entity")
        || type.isAnnotatedWith("org.springframework.data.mongodb.core.mapping.Document")
        || type.isAssignableTo("org.springframework.data.repository.Repository");
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
