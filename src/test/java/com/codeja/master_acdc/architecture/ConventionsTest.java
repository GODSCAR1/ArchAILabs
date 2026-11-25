package com.codeja.master_acdc.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/** Convenciones del dominio, de los eventos y del ensamblado (lab S18, ADR 0012 §3 y §4). */
@AnalyzeClasses(
    packages = "com.codeja.master_acdc",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ConventionsTest {

  static final String EVENTS = "..domain.event..";

  /** Agregado: clase de domain.model que se reconstruye con una factoría estática restore(...). */
  static final DescribedPredicate<JavaClass> AGGREGATES =
      DescribedPredicate.describe(
          "aggregates (domain.model classes with a static restore factory)",
          type ->
              type.getPackageName().contains(".domain.model")
                  && type.getMethods().stream()
                      .anyMatch(
                          m ->
                              "restore".equals(m.getName())
                                  && m.getModifiers().contains(JavaModifier.STATIC)));

  static final DescribedPredicate<JavaClass> ANONYMOUS_CLASSES =
      DescribedPredicate.describe("anonymous classes", JavaClass::isAnonymousClass);

  // ---------- Eventos ----------

  @ArchTest
  static final ArchRule domain_events_are_records_in_domain =
      CompositeArchRule.of(
              classes()
                  .that()
                  .resideInAPackage(EVENTS)
                  .and()
                  .areNotInterfaces()
                  .and()
                  .doNotHaveSimpleName("package-info")
                  .should()
                  .beRecords())
          .and(
              classes()
                  .that()
                  .implement(resideInAPackage(EVENTS))
                  .should()
                  .resideInAPackage(EVENTS))
          .because(
              "un evento de dominio es un hecho inmutable del agregado: decláralo como record en"
                  + " domain/event (y en el OrderEvent sellado si es de ordering), nunca en un"
                  + " adaptador");

  @ArchTest
  static final ArchRule domain_events_are_created_only_inside_the_domain =
      freeze(
          noClasses()
              .that()
              .resideOutsideOfPackage("..domain..")
              .should()
              .callConstructorWhere(target(owner(resideInAPackage(EVENTS))))
              .because(
                  "el agregado registra sus eventos en cada transición y el servicio los recoge con"
                      + " pullEvents() (ADR 0012 §3): no construyas el evento en el servicio ni en"
                      + " un adaptador"));

  @ArchTest
  static final ArchRule event_listeners_live_only_in_adapters =
      methods()
          .that()
          .areMetaAnnotatedWith("org.springframework.context.event.EventListener")
          .or()
          .areAnnotatedWith("org.springframework.context.event.EventListener")
          .should()
          .beDeclaredInClassesThat()
          .resideInAPackage("..infrastructure.adapters..")
          .because(
              "escuchar un evento de Spring es un detalle de entrega: el listener es un adaptador"
                  + " de infrastructure/adapters que traduce el evento y llama a un puerto; un caso"
                  + " de uso nunca lleva @EventListener ni @TransactionalEventListener");

  // ---------- Inyección ----------

  @ArchTest
  static final ArchRule no_field_injection =
      GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.because(
          "las dependencias entran por el constructor y las conecta un @Bean de"
              + " infrastructure/config: así el núcleo se prueba sin Spring");

  // ---------- Reglas propias (fugas reales del ADR 0012) ----------

  @ArchTest
  static final ArchRule aggregates_are_created_only_through_their_factories =
      noClasses()
          .that()
          .resideOutsideOfPackage("..domain..")
          .should()
          .callConstructorWhere(target(owner(AGGREGATES)))
          .because(
              "un agregado nace con place(...), que valida sus invariantes, o vuelve de la base de"
                  + " datos con restore(...) (ADR 0012 §4): su constructor es privado y nadie fija"
                  + " el estado desde fuera");

  @ArchTest
  static final ArchRule aggregates_are_restored_only_by_persistence_adapters =
      noClasses()
          .that()
          .resideOutsideOfPackages("..domain..", "..infrastructure.adapters.out.persistence..")
          .should()
          .callMethodWhere(target(name("restore")).and(target(owner(AGGREGATES))))
          .because(
              "restore(...) reconstruye sin reglas de negocio y solo lo usa el mapper de"
                  + " persistencia (ADR 0012 §4): para crear usa place(...) y para cambiar de"
                  + " estado, una transición del agregado (confirm, cancel, refund...)");

  @ArchTest
  static final ArchRule inbound_adapters_never_receive_aggregates =
      noClasses()
          .that()
          .resideInAPackage("..infrastructure.adapters.in..")
          .should()
          .dependOnClassesThat(AGGREGATES)
          .because(
              "los casos de uso devuelven un resultado de aplicación (OrderDetail, OrderSummary),"
                  + " nunca el agregado (ADR 0012 §3): añade al resultado el dato que necesite la"
                  + " web");

  @ArchTest
  static final ArchRule output_ports_do_not_expose_transactions =
      noClasses()
          .that()
          .resideInAPackage("..domain.port..")
          .should()
          .haveSimpleNameContaining("Transaction")
          .orShould()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework.transaction..", "java.sql..", "javax.sql..")
          .because(
              "un puerto de salida habla en términos del negocio: si una operación debe ser"
                  + " atómica, lo dice su contrato y lo garantiza el adaptador (OrderPayments, ADR"
                  + " 0012 §3)");

  @ArchTest
  static final ArchRule config_does_not_implement_ports_with_anonymous_classes =
      noClasses()
          .that()
          .resideInAPackage("..infrastructure.config..")
          .and(ANONYMOUS_CLASSES)
          .should()
          .implement(resideInAnyPackage("..domain.port..", "..application.port.."))
          .because(
              "un puerto se implementa en un adaptador con nombre de infrastructure/adapters/out,"
                  + " que se puede probar y encontrar; config solo lo instancia con @Bean (ADR"
                  + " 0012, fuga de PaymentTransactions)");
}
