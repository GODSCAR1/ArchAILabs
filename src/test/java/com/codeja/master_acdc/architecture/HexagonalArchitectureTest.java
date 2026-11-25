package com.codeja.master_acdc.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Reglas del hexágono para todos los módulos (lab S18, ADR 0012). Los nombres de los campos son las
 * decisiones: es lo que aparece en el informe cuando una regla falla.
 */
@AnalyzeClasses(
    packages = "com.codeja.master_acdc",
    importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

  static final String DOMAIN = "..domain..";
  static final String APPLICATION = "..application..";
  static final String ADAPTERS = "..infrastructure.adapters..";
  static final String CONFIG = "..infrastructure.config..";

  /** Lista negra: frameworks y librerías técnicas que nunca entran en el núcleo. */
  static final String[] FRAMEWORKS = {
    "org.springframework..",
    "jakarta.persistence..",
    "jakarta.transaction..",
    "jakarta.validation..",
    "jakarta.servlet..",
    "org.hibernate..",
    "com.fasterxml.jackson..",
    "tools.jackson..",
    "org.bson..",
    "com.mongodb..",
    "io.micrometer.."
  };

  // ---------- Dominio ----------

  @ArchTest
  static final ArchRule domain_is_free_of_frameworks =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORKS)
          .because(
              "el dominio es Java puro (ADR 0012): quita la anotación o el tipo técnico del"
                  + " dominio, crea su equivalente en un adaptador de infrastructure/adapters y"
                  + " traduce con un mapper estático; si el núcleo necesita algo del exterior,"
                  + " declara un puerto en domain/port");

  @ArchTest
  static final ArchRule domain_only_depends_on_java_and_domain =
      classes()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", DOMAIN, "org.jspecify..")
          .because(
              "lista blanca del dominio (ADR 0012, concesión 7: solo JSpecify además de java.*):"
                  + " cualquier otra dependencia es una fuga; tradúcela en un adaptador o pide la"
                  + " concesión en el ADR antes de añadirla aquí");

  // ---------- Aplicación ----------

  @ArchTest
  static final ArchRule application_is_free_of_frameworks =
      noClasses()
          .that()
          .resideInAPackage(APPLICATION)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORKS)
          .because(
              "los casos de uso son Java puro (ADR 0012 §5): las transacciones se aplican en"
                  + " infrastructure/config con TransactionTemplate, y la cache, la seguridad o los"
                  + " listeners van en un decorador o un adaptador de infrastructure/adapters");

  @ArchTest
  static final ArchRule application_only_depends_on_java_domain_and_application =
      classes()
          .that()
          .resideInAPackage(APPLICATION)
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", DOMAIN, APPLICATION, "org.jspecify..")
          .because(
              "la aplicación orquesta el dominio a través de puertos: si necesitas una librería,"
                  + " ponla detrás de un puerto de domain/port implementado en"
                  + " infrastructure/adapters/out");

  @ArchTest
  static final ArchRule ports_are_interfaces_or_records =
      classes()
          .that()
          .resideInAnyPackage("..application.port..", "..domain.port..")
          .and()
          .doNotHaveSimpleName("package-info")
          .should()
          .beInterfaces()
          .orShould()
          .beRecords()
          .because(
              "un puerto es un contrato: interfaces para los casos de uso y las necesidades del"
                  + " núcleo, records para comandos y resultados; la lógica va en application o en"
                  + " el agregado");

  // ---------- Capas ----------

  @ArchTest
  static final ArchRule layers_only_point_towards_the_domain =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Domain")
          .definedBy(DOMAIN)
          .layer("Application")
          .definedBy(APPLICATION)
          .layer("Api")
          .definedBy("com.codeja.master_acdc.*.api..")
          .layer("Adapters")
          .definedBy(ADAPTERS)
          .layer("Config")
          .definedBy(CONFIG)
          .whereLayer("Config")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Adapters")
          .mayOnlyBeAccessedByLayers("Config")
          .whereLayer("Api")
          .mayOnlyBeAccessedByLayers("Adapters", "Config")
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("Api", "Adapters", "Config")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Application", "Api", "Adapters", "Config")
          .because(
              "las dependencias apuntan hacia el dominio (ADR 0012): el núcleo nunca conoce un"
                  + " adaptador ni la configuración; si lo necesitas, invierte la dependencia con"
                  + " un puerto y deja que config conecte la implementación");

  // ---------- Adaptadores ----------

  @ArchTest
  static final ArchRule adapters_do_not_know_each_other =
      freeze(
          slices()
              .matching("com.codeja.master_acdc.(*).infrastructure.adapters.(*).(*)..")
              .should()
              .notDependOnEachOther()
              .because(
                  "cada adaptador habla solo con el núcleo: si uno necesita algo de otro, sube esa"
                      + " necesidad a un puerto (o a un caso de uso) y deja que config los"
                      + " conecte"));

  @ArchTest
  static final ArchRule inbound_adapters_do_not_use_outbound_ports =
      noClasses()
          .that()
          .resideInAPackage("..infrastructure.adapters.in..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..domain.port..")
          .because(
              "un adaptador de entrada solo llama a puertos de entrada (application/port): si"
                  + " necesita datos, crea o amplía un caso de uso que los pida a su puerto de"
                  + " salida");

  @ArchTest
  static final ArchRule adapter_classes_are_public_only_when_config_wires_them =
      freeze(
          classes()
              .that()
              .resideInAPackage(ADAPTERS)
              .and()
              .areTopLevelClasses()
              .and()
              .doNotHaveSimpleName("package-info")
              .and()
              .areNotAnnotatedWith("jakarta.persistence.metamodel.StaticMetamodel")
              .should(bePackagePrivateUnlessWiredByConfig())
              .because(
                  "un adaptador es un detalle de su paquete: solo es público si"
                      + " infrastructure/config lo instancia con @Bean; quita el public a DTOs,"
                      + " mappers, entidades y controladores, y si algo deja de compilar, lo que"
                      + " falta es un puerto"));

  private static ArchCondition<JavaClass> bePackagePrivateUnlessWiredByConfig() {
    return new ArchCondition<>("be package-private unless infrastructure.config wires them") {
      @Override
      public void check(JavaClass type, ConditionEvents events) {
        if (!type.getModifiers().contains(JavaModifier.PUBLIC)) {
          return;
        }
        boolean wiredByConfig =
            type.getDirectDependenciesToSelf().stream()
                .anyMatch(
                    d -> d.getOriginClass().getPackageName().contains(".infrastructure.config"));
        if (!wiredByConfig) {
          events.add(
              SimpleConditionEvent.violated(
                  type,
                  type.getName() + " es public y ninguna clase de infrastructure.config lo usa"));
        }
      }
    };
  }
}
