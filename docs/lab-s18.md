# Lab S18 — Reglas ArchUnit que protegen la arquitectura hexagonal

Rama `lab/s18`. Reglas en `src/test/java/com/codeja/master_acdc/architecture/`. Fuente de las reglas: ADR
`docs/adr/0012-arquitectura-hexagonal.md`. Fronteras: `docs/context-map.md`. Evidencias:
`docs/evidencias/s18/`.

## Adaptación del enunciado al repo

| Enunciado | Repo | Por qué |
|---|---|---|
| `dev.codeja.shop.architecture` | `com.codeja.master_acdc.architecture` | Paquete raíz real. Las 8 clases de reglas por módulo de labs anteriores siguen en `arch/` |
| `adapter.in.*` / `adapter.out.*` / `config` | `infrastructure.adapters.in.*` / `infrastructure.adapters.out.*` / `infrastructure.config` | Convención del repo (ADR 0012, concesión 1) |
| `application.port.in` / `application.port.out` | `application.port` (entrada) / `domain.port` (salida) | ADR 0012, concesión 1 |
| API pública = `domain` + `application.port.in` | Paquete `api` de cada módulo; `pricing` (núcleo compartido sin fachada) publica `domain.model` | Decisión del lab S12: `inventory.domain` no es público. Ver `docs/context-map.md` |
| `shared` y `platform` abiertos | No existen | — |
| Ninguna clase de `adapter` es pública | `adapter_classes_are_public_only_when_config_wires_them` | Los beans se crean con `@Bean` en `infrastructure.config`, que es otro paquete: lo que `config` instancia tiene que ser público. El resto (DTOs, mappers, entidades JPA, controladores, `@RestControllerAdvice`) no |
| Listeners solo en `adapter.in.event` | `event_listeners_live_only_in_adapters` | Los dos listeners del repo (`OrderCancelledListener`, `ProductChangedCacheListener`) reaccionan a un evento del propio módulo y llaman a otro módulo o a la cache: son adaptadores de salida. Llevarlos a `in/event` haría que un adaptador de entrada dependiera de uno de salida |
| `freeze.store.default.path=src/test/resources/archunit-store` | `src/test/resources/archunit_store` | La línea base ya existía (lab S12) |
| Módulos en capas por migrar | No queda ninguno | Todos se hicieron hexagonales en labs anteriores (ver `docs/lab-s17.md`). La deuda congelada es la que encontraron las reglas genéricas en los módulos que no son `ordering` |

## Paso 2: punto de partida

Las reglas se ejecutaron contra el build de `main` después del S17, antes de tocar código (`docs/evidencias/s18/linea-base.txt`):

| Regla | catalog | exports | identity | inventory | ordering | pricing |
|---|---|---|---|---|---|---|
| `domain_is_free_of_frameworks` | 0 | 0 | 0 | 0 | 0 | 0 |
| `domain_only_depends_on_java_and_domain` | 0 | 0 | 0 | 0 | 0 | 0 |
| `application_is_free_of_frameworks` | 0 | 0 | 0 | 0 | 0 | 0 |
| `application_only_depends_on_java_domain_and_application` | 0 | 0 | 0 | 0 | 0 | 0 |
| `ports_are_interfaces_or_records` | 0 | 0 | 0 | 0 | 0 | 0 |
| `layers_only_point_towards_the_domain` | 0 | 0 | 0 | 0 | 0 | 0 |
| `adapters_do_not_know_each_other` | 0 | 0 | **1** | 0 | 0 | — |
| `inbound_adapters_do_not_use_outbound_ports` | 0 | 0 | 0 | 0 | 0 | — |
| `adapter_classes_are_public_only_when_config_wires_them` | **27** | **3** | **27** | **5** | **15** | — |
| `modules_are_free_of_cycles` | 0 | 0 | 0 | 0 | 0 | 0 |
| `modules_only_use_the_published_api_of_other_modules` | 0 | 0 | 0 | 0 | **10** | 0 |
| `domain_events_are_created_only_inside_the_domain` | **2** | 0 | 0 | 0 | 0 | 0 |
| Resto de `ConventionsTest` | 0 | 0 | 0 | 0 | 0 | 0 |

El dominio y la aplicación de todos los módulos ya cumplían las listas negra y blanca. Esperable: no queda
ningún módulo en capas. La deuda está en los adaptadores y en las fronteras:

- **Adaptadores públicos (77).** DTOs, mappers, entidades JPA y controladores públicos sin que `config` los
  use. Los 15 de `ordering` se corrigieron (paso 4). Los demás se congelan.
- **`identity`: un adaptador conoce a otro.** `CurrentOrderActorAdapter` (`adapters/out/ordering`) usa
  `UserAccountJpaRepository` y `OrderActorPersistenceMapper` (`adapters/out/persistence`). Corrección: un
  puerto `AccountCustomerLookup` en `identity.domain.port`, implementado en `persistence`.
- **`ordering` → `inventory.domain` (10).** Lo provoca `InventoryFacade`, que publica `StockLine` y
  `ReservationResult` del dominio de inventory. Es la misma deuda que congeló el lab S12.
- **`catalog` crea sus eventos fuera del dominio (2).** `EditProductService` y `CatalogStockProjectionJob`
  hacen `new ProductChanged(...)`: `Product` no es un agregado con `pullEvents()`.

## Pasos 3 a 6: reglas

20 reglas en 3 clases. Cada `because` dice qué hacer, no solo qué está prohibido.

| Clase | Reglas |
|---|---|
| `HexagonalArchitectureTest` | `domain_is_free_of_frameworks` (lista negra), `domain_only_depends_on_java_and_domain` (lista blanca: `java..`, `..domain..` y `org.jspecify..`, concesión 7), `application_is_free_of_frameworks`, `application_only_depends_on_java_domain_and_application`, `ports_are_interfaces_or_records`, `layers_only_point_towards_the_domain` (Domain, Application, Api, Adapters, Config con `consideringOnlyDependenciesInLayers()`), `adapters_do_not_know_each_other`, `inbound_adapters_do_not_use_outbound_ports`, `adapter_classes_are_public_only_when_config_wires_them` |
| `ModuleBoundariesTest` | `modules_are_free_of_cycles`, `modules_only_use_the_published_api_of_other_modules` (condición propia: paquete publicado y solo desde `infrastructure`) |
| `ConventionsTest` | `domain_events_are_records_in_domain`, `domain_events_are_created_only_inside_the_domain`, `event_listeners_live_only_in_adapters`, `no_field_injection` (`GeneralCodingRules`) y 6 reglas propias, sacadas de las fugas reales del ADR 0012: `aggregates_are_created_only_through_their_factories`, `aggregates_are_restored_only_by_persistence_adapters`, `inbound_adapters_never_receive_aggregates`, `output_ports_do_not_expose_transactions`, `config_does_not_implement_ports_with_anonymous_classes` |

En la lista negra no hay concesión de transacciones: el ADR 0012 las aplica en `config` con
`TransactionTemplate`, así que `application` no depende de `spring-tx`.

**Paso 4: la regla de adaptadores públicos falló en `ordering`, como anunciaba el enunciado.** Se quitó el
`public` a 15 clases: 8 de `adapters/in/web` (3 controladores, `OrderExceptionHandler`, 3 DTOs y
`OrderWebMapper`) y 7 de `adapters/out/persistence` (2 entidades, el embeddable, 3 mappers y
`OrderSummaryRow`). Ninguna clase de fuera de su paquete las usaba, ni en `src/main` ni en `src/test`: no
apareció ninguna dependencia oculta. `exports.ExportWebMapper` también se corrigió, para probar en el paso 8
que la línea base baja sola.

**Paso 5: contraste con el mapa de contextos.** No existía `docs/context-map.md`, así que se creó a partir de
las dependencias reales. Ninguna dependencia contradice el mapa. La única que no es limpia
(`ordering` → `inventory.domain.model`) queda documentada como deuda de `inventory.api`.

## Paso 7: cada fuga, provocada y revertida

Cada fuga se introdujo en el código, se compiló, se ejecutaron las 20 reglas y se revirtió. Detalle con el
mensaje completo de cada regla en `docs/evidencias/s18/mutaciones.log`.

| Fuga provocada | Regla que debe fallar | ¿Falla? | ¿El mensaje dice cómo corregirla? | Corrección |
|---|---|---|---|---|
| `@jakarta.persistence.Entity` (nombre completo, sin import) en `Order` | `domain_is_free_of_frameworks` | Sí (+ lista blanca) | Sí | Quitarla: la entidad es `OrderJpaEntity`, traducida por `OrderPersistenceMapper` |
| `@JsonProperty` en `Money` (`pricing.domain.model`; ordering no tiene `Money`) | `domain_is_free_of_frameworks` | Sí (+ lista blanca) | Sí | El nombre JSON se decide en el DTO del adaptador web |
| `Page<Order>`/`Pageable` en `OrderRepository` (`OrderStore`) | `domain_is_free_of_frameworks` | Sí | Sí | Tipo de página propio en el dominio (como `ProductSearchPage`) |
| `@Cacheable` en `GetOrderService` | `application_is_free_of_frameworks` | Sí (+ lista blanca) | Sí | Decorador de cache en `infrastructure` (como `ProductDetailCache`) |
| `OrderController` usa `OrderJpaRepository` | `adapters_do_not_know_each_other` | Sí | Sí | Llamar a un caso de uso |
| `OrderJpaEntity` vuelve a ser `public` | `adapter_classes_are_public_only_when_config_wires_them` | Sí | Sí | Package-private |
| `catalog` usa `CreateOrderService` (el `PlaceOrderService` del lab) | `modules_only_use_the_published_api_of_other_modules` | Sí (+ `modules_are_free_of_cycles`) | Sí | Si catalog necesitara crear pedidos, lo haría por una fachada de `ordering.api` |
| El dominio de `ordering` usa `catalog.domain.model.Product` | `modules_only_use_the_published_api_of_other_modules` | Sí | Sí | Puerto propio en `ordering.domain.port` y adaptador en `adapters/out/catalog` |
| `@TransactionalEventListener` en `CancelOrderService` | `event_listeners_live_only_in_adapters` | Sí (+ las dos de aplicación) | Sí | Listener en un adaptador |
| `if` de negocio en `OrderController` (rechazar más de 10 líneas) | Ninguna | **No** | — | ArchUnit no ve lógica. Lo cubren otras capas de Verify (ver abajo) |
| S17: estado fijado desde config (`Order.restore(..., PAID, ...)` en `ConfirmationConfiguration`) | `aggregates_are_restored_only_by_persistence_adapters` | Sí | Sí | `confirm()` y `registerPayment()` del agregado |
| S17: constructor del agregado accesible (público y llamado desde un servicio) | `aggregates_are_created_only_through_their_factories` | Sí | Sí | Constructor privado, `place`/`restore` |
| S17: el agregado llega a la web (`OrderWebMapper` recibe `Order`) | `inbound_adapters_never_receive_aggregates` | Sí | Sí | `OrderDetail` |
| S17: `PaymentTransactions` en `domain/port` | `output_ports_do_not_expose_transactions` | Sí | Sí | `OrderPayments` con operaciones atómicas |
| S17: clase anónima que implementa `PublishOrderEvents` en `OrderingConfiguration` | `config_does_not_implement_ports_with_anonymous_classes` | Sí | Sí | Adaptador con nombre (`OrderEventsAdapter`) |
| S17: el servicio construye `OrderCancelled` | `domain_events_are_created_only_inside_the_domain` | Sí | Sí | `pullEvents()` |

**Fugas del agente (`docs/lab-s17.md`).** La segunda pasada sobre `exports` no introdujo ninguna fuga de
arquitectura. Su único hallazgo, la caracterización incompleta (sin 401 ni 403), no es de ArchUnit. Por eso
las filas S17 son las fugas reales que se corrigieron en `ordering` (tabla de fugas del ADR 0012).

**Lo que salió del paso:**

- **Fuga sin regla.** «Invariantes saltables» (constructor público del agregado) no la detectaba ninguna regla
  del enunciado. Se escribió `aggregates_are_created_only_through_their_factories`.
- **La lista blanca del dominio no basta para las fronteras.** `..domain..` incluye el dominio de cualquier
  módulo, así que `ordering.domain` → `catalog.domain` pasa la lista blanca. Lo detecta la regla de módulos.
  Es la pista del enunciado, y por eso las dos reglas se complementan.
- **El nombre completo ya no se escapa.** El grep provisional del S17 no veía `@jakarta.persistence.Entity`
  escrito sin import. ArchUnit lo detecta porque lee el bytecode.
- **El `if` de negocio en el controlador no lo detecta ArchUnit.** Lo cubren:
  - `OrderApiCharacterizationIT`, si cambia una respuesta existente;
  - la guía de `AGENTS.md` (las reglas y las transiciones viven en el agregado; sin lógica de negocio en métodos `@Bean`);
  - la revisión del diff.

  Una regla nueva de negocio en el lugar equivocado solo la para la revisión o PIT. PIT no muta la capa web.

## Paso 8: deuda congelada

Las reglas que fallan por código de otros módulos van envueltas en `freeze(...)`. Las que `ordering` cumple
por completo no se congelan: `ordering` sí está en `modules_only_use_the_published_api_of_other_modules`, pero
lo provoca la fachada de inventory.

| Regla congelada | Violaciones congeladas | Módulos |
|---|---|---|
| `adapter_classes_are_public_only_when_config_wires_them` | 61 | catalog 27, identity 27, inventory 5, exports 2 |
| `adapters_do_not_know_each_other` | 1 (5 líneas de detalle) | identity |
| `modules_only_use_the_published_api_of_other_modules` | 10 | ordering → inventory.domain |
| `domain_events_are_created_only_inside_the_domain` | 2 | catalog |

Comprobaciones (primero con el runner del lab y después con Maven en local; ver «Verificación final»):

1. Con `allowStoreCreation=true` se crea la línea base. Con `false`, la segunda ejecución está en verde.
2. Al corregir `ExportWebMapper` (público → package-private), la línea base pasó de 62 a 61 violaciones sin
   regenerarla. FreezingArchRule borra sola las violaciones que ya no existen.
3. Al añadir en catalog un DTO público nuevo (`ProductBannerResponse`), la regla congelada falló con esa
   única violación: congelar solo tolera lo que ya había.

## Verificación final (Maven, en local)

| Comprobación | Resultado |
|---|---|
| Creación de la línea base (`allowStoreCreation=true`, una vez) | 20 tests en verde; 4 entradas nuevas en `archunit_store/` con 61 + 5 + 10 + 2 líneas, las mismas que en la prueba previa (`docs/evidencias/s18/creacion-linea-base.log`) |
| Segunda pasada con `allowStoreCreation=false` | 20 tests en verde, sin crear nada |
| `verify-quality.sh --full` | Verde: 477 tests unitarios y 521 de integración, 0 fallos. `OrderApiCharacterizationIT` (25 tests) en verde con las 15 clases de ordering ya package-private. PIT de pricing 96 %. La línea base no cambió (`docs/evidencias/s18/verify-full.log`) |

La primera ejecución de `--full` falló en la capa 1: PMD señaló `LiteralsFirstInComparisons` y
`CognitiveComplexity` en las propias reglas. Se corrigió extrayendo `violation(...)` en `ModuleBoundariesTest`,
sin cambiar los mensajes, así que la línea base siguió valiendo. Las reglas pasan las mismas capas de Verify
que el código de producción.

## Paso 9: prueba con el agente

_Pendiente._

## Paso 10: el loop protegido

| Capa | Qué se añadió |
|---|---|
| Guide | `AGENTS.md`, sección «Reglas de ArchUnit»: una frase por regla con el nombre del test, enlazada al ADR 0012 y a `docs/context-map.md`. Las marcas «(pendiente: ArchUnit en S17)» de las reglas que ya tienen verificador se sustituyeron por su nombre |
| Verify | Las reglas se ejecutan con los tests unitarios de `./verify-quality.sh --fast` (y `--full`). Se eliminó el grep provisional del lab S17 (imports de `domain`/`application` y agregado en la web): `domain_is_free_of_frameworks`, `application_is_free_of_frameworks` e `inbound_adapters_never_receive_aggregates` lo sustituyen |
| Verify (zona protegida) | `protect-verify.sh` (Claude Code) y `verify-quality.ts` (OpenCode) bloquean editar `src/test/java/com/codeja/master_acdc/arch/` y `architecture/`, `archunit.properties` y `archunit_store/`, también desde la shell. Además bloquean regenerar la línea base desde la línea de comandos (`allowStoreCreation`, `freeze.refreeze`, `freeze.store`). Las tres clases nuevas están en `scripts/protected-tests.txt`, así que `verify-quality.sh` falla si alguna desaparece |
| Solve | La tabla del paso 7: fuga, regla que la detecta y corrección |

De paso se cerró un hueco: `IdentityArchitectureTest`, `IntegrationTestRulesTest` y `ModuleDependenciesTest`
no estaban en la zona protegida. Ahora está protegido todo el directorio `arch/`.
