# Design

## Context

Véase proposal.md para motivación. Se han contrastado AGENTS.md, docs/adr/0007, 0009 y 0010, el enunciado S12, los artefactos de add-authentication y el código actual. HttpSecurityConfiguration autentica pero no convierte roles ni activa seguridad de método. JwtTokenAdapter emite siete claims, sin sv. UserAccount.Profile conserva roles como nombres y customerId nullable.

OrderController interpreta X-Customer-Id y mapea directamente; OrderResponse/OrderSummaryResponse contienen conversiones heredadas. OrderingConfiguration publica lambdas transaccionales. OrderJpaRepository filtra cancelación por cliente y realiza UPDATE condicional por estado; su listado findByStatus no filtra propiedad y proyecta métricas con count(l.productId). El guardián de conteo invoca adapter.findByStatus(OrderStatus.PAID). V3 usa status VARCHAR(16) sin CHECK de estados: REFUNDED cabe sin migración. IdentityArchitectureTest ya prohíbe ordering/inventory → identity.

## Goals / Non-Goals

**Goals:** comprobar permisos al entrar por beans públicos, limitar filas antes de materializar objetos, mantener transacciones y efectos exactamente una vez, medir consultas sin N+1 y preservar direcciones de módulos.

**Non-Goals:** mover customers, duplicar persistencia de identity en ordering, convertir los comandos internos de inventario en endpoints de usuario o añadir dependencias. Paginación administrativa mínima: orden fijo id, sin buscador.

## Decisions

### 1. Roles y seguridad de método

Role vive en identity.domain.model, con conjuntos inmutables de permisos según proposal. Mantener nombres en UserAccount.Profile evita modificar el formato persistido y permite comprobar roles desconocidos. El conversor en identity.infrastructure adapta únicamente roles conocidos y añade ROLE_<name> y sus permisos, deduplicados. No usa scope/scp como permisos ni da ROLE_UNKNOWN. Probar roles vacíos, desconocidos, mezcla y duplicados; claims roles mal tipados no conceden acceso. Mantener firma/issuer/audience/kid/exp y siete claims actuales.

Activar @EnableMethodSecurity en infraestructura. En HttpSecurityConfiguration conectar el conversor y añadir /api/admin/** hasRole("ADMIN") antes del authenticated general. Mantener las otras cadenas, CSRF y handlers. Los fallos de método deben llegar a SecurityProblems como 403, nunca al manejador genérico de 500.

Decoradores públicos **no final**, métodos públicos no final, sobre interfaces de caso de uso; conservar CGLIB por defecto. Registrar solo la fachada decorada como bean que consumen controladores; delegados puros construidos dentro de configuración, no publicados como vía alternativa. Orden: proxy de autorización → resolución de identidad/alcance → frontera TransactionTemplate → servicio puro. No auto-invocación. MethodAuthorizationTest obtiene el bean del contexto y comprueba permiso denegado sin invocar delegado y permitido invocándolo; una prueba HTTP real complementa la inspección AopUtils.isAopProxy, que sola no demuestra seguridad.

| Comando HTTP | Decorador y permiso |
|---|---|
| Crear/editar producto | CreateProductUseCase / EditProductUseCase, product:write |
| Reserva directa | ReserveProductStockUseCase, stock:reserve |
| Crear pedido | CreateOrderUseCase, order:create |
| Cancelar pedido | CancelOrderUseCase, order:cancel-own |
| Reembolsar pedido | nuevo RefundOrderUseCase, order:refund |

Las lecturas de pedidos usan hasAnyAuthority('order:read-own','order:read-any'); listado administrativo hasAuthority('user:read'). La resolución de permisos no pertenece a application.

**Conflicto de lectura literal de «cada comando»:** Register/Login/Refresh/Logout tienen contratos públicos/CSRF; ChangePassword es autenticado por sujeto, sin permiso de negocio adicional. ReserveStockUseCase y ReleaseStockUseCase son internos al checkout/evento y no deben exigir stock:reserve, pues CUSTOMER no lo tiene y rompería creación/cancelación. Se protegen todos los comandos HTTP de negocio de esta tabla; no se cambia esa frontera interna. ArchUnit enumerará esta frontera explícita, sin fingir que todos los servicios internos llevan permisos RBAC.

### 2. Identidad sin ciclo

Mantener identity.infrastructure → ordering.api. Añadir en ordering.api una interfaz mínima CurrentOrderActor con un resultado inmutable que distinga cuenta inexistente, cuenta sin cliente y cliente presente, sin Spring. Un adaptador de identity.infrastructure implementa esa interfaz: toma el sub del Authentication validado y consulta una proyección de user_account por PK (id → customer_id). Un adaptador local de ordering.infrastructure consume su propia API y produce el alcance de dominio (propietario o lectura global) usando las authorities. Ningún servicio de application importa esa API: solo recibe comandos/criterios de dominio ya resueltos.

Esta interfaz es contrato público entre módulos, no un puerto de salida de application; no contradice la regla de domain/port. Los puertos de persistencia y consulta del dominio permanecen allí. El bean del puente se ensambla en identity.infrastructure.config. Se descarta ordering → identity.api porque introduciría ciclo; se descarta pasar customerId en JWT porque viola claims y se vuelve obsoleto; se descarta consultar la tabla ajena desde ordering.

Después del permiso, cada petición de pedidos resuelve una sola vez la cuenta por PK, incluso para lectura global; no cache L1/L2 ni lookup por email. Cuenta inexistente: 403 forbidden sin consultar pedidos; cliente null: crear 403, lectura propia lista vacía/detalle 404, cancelación 404. SUPPORT/ADMIN con cuenta existente y cliente null pueden leer globalmente y reembolsar. Validación de UUID de sub falla cerrada. No se introduce validación de sv/enabled en este change.

Para un usuario multirrol con order:cancel-own y order:read-any, la cancelación admite alcance global tal como pide «salvo order:read-any». SUPPORT/ADMIN solos siguen dando 403 porque falta cancel-own. Añadir test explícito de esta combinación para no confundir alcance con permiso.

### Reglas de arquitectura

- ModuleDependenciesTest: slices de primer nivel com.codeja.master_acdc.(*).. libres de ciclos, sin exclusiones de identity, ordering, inventory o catalog.
- identity.infrastructure → ordering.api permitido; identity → ordering.domain/application/infrastructure prohibido. ordering e inventory → identity prohibido.
- ordering.infrastructure → inventory.api permitido; inventory → ordering prohibido. Efectos de cancelación mediante el evento existente.
- catalog conserva su frontera de proyección de stock; ordering/inventory no leen catálogo cacheado. No nuevas dependencias entre estos módulos.
- domain sin Spring/JPA/Jackson; application solo su dominio/capa de aplicación. APIs de módulos sin frameworks. Puertos de salida en domain/port; ensamblaje/transacciones en infrastructure/config.
- AuthorizationArchitectureTest: decoradores de los comandos HTTP enumerados tienen @PreAuthorize y son proxificables; controladores de pedidos no reciben customerId ni X-Customer-Id. Mappers finales estáticos en infraestructura. Añadir pruebas sin editar guardianes existentes.

### 3. Pedidos, consultas y mapeo

Eliminar cabecera, parseCustomerId y excepciones missing/invalid-customer-id de la frontera HTTP. Retirar customerId de OrderResponse; conservar el dato en dominio/persistencia. Extraer conversiones heredadas a OrderWebMapper y OrderPersistenceMapper, más mapper de resúmenes; DTOs y entidades no convierten. Los parámetros de entrada no permiten seleccionar propietario; la cabecera antigua se ignora como cabecera no soportada y nunca otorga autoridad. Cuerpos solo contienen líneas; cualquier campo adicional customerId no participa en mapeo.

ListOrdersByStatusUseCase recibe un criterio de dominio con estado y alcance explícito; no usar null como «todos». Consulta JPA tipada con Specification y metamodelo, allOf para estado/propietario; proyección agregada conserva LEFT JOIN, count(l.productId), coalesce(sum(...),0), cliente sin nombre y colección vacía. Proponer reemplazar findByStatus(status) por consulta con criterio obligatorio: la rotura del guardián de conteo debe resolverla una persona **antes** de cambiar la firma. No mantener sobrecarga global de compatibilidad para evitarlo.

Detalle usa id + alcance en la consulta, con EntityGraph de una sola colección (lines). Cancelación lee con id + alcance y repite ese predicado en UPDATE condicional, conservando comprobación PENDING/PAID. No recuperar por id global y comparar cliente en Java ni filtrar listas en memoria. Nuevos criterios combinados tipados, consultas parametrizadas, sin SQL/JPQL dinámico concatenado. La proyección del listado no hace consultas por fila.

RefundOrderService valida PAID/SHIPPED en dominio y guarda mediante UPDATE condicional por estado dentro de TransactionTemplate. Un resultado de cero filas tras lectura válida se traduce a order-not-refundable (409); id ausente a order-not-found (404). No publicar OrderCancelled ni llamar InventoryFacade. La cancelación existente y el reembolso compiten por el mismo estado con updates condicionales: solo uno puede ganar desde PAID; probar carreras con PostgreSQL real. No modificar firmas protegidas por iniciativa propia.

Para garantizar 403 antes de 404, autorización precede al parseo de id y a consultas de propiedad. Para creación, permiso/cliente preceden a validaciones de negocio de líneas; JSON ilegible sigue siendo error de transporte. Dividir controladores si añadir casos de uso excede cinco parámetros, sin contenedores de dependencias de compatibilidad.

### 4. Administración

Nuevo ListUsersUseCase/Service en identity, puerto de consulta en domain/port y proyección de lectura dedicada. Seleccionar únicamente id/email/roles/enabled, nunca cargar entidades con hash para después descartarlo. PageRequest de infraestructura con sort id ASC fijo, page=0/size=20, máximo 100 y rechazo de sort externo. DTO de respuesta propio con content/page/size/totalElements/totalPages y mapper estático; no exponer Page de Spring al dominio.

No cache ni asociación a refresh/customers en la consulta. GET tiene hasRole ADMIN en cadena y hasAuthority user:read en decorador. Probar por separado ambos controles con contexto de seguridad de test deliberadamente incompleto (no sustituye la matriz de tokens reales).

### 5. Verificación y coste

SecurityMatrixTest (*Test*, fast loop) usa @SpringBootTest + MockMvc con cadenas reales, PostgreSQL Testcontainers y las fixtures existentes de Mongo/Redis; dependencias sanas para health 200. Crear por **cada invocación** cuentas con IDs únicos, clientes, pedido propio/ajeno, productos y stock; no reutilizar el estado mutado por filas anteriores. PAID/SHIPPED se siembran directamente porque no hay API de pago/envío. A01 reemplaza A1 porque Sku exige ^[A-Z0-9-]{3,64}$; para POST usar SKU único válido y para PUT versión actual. Aislar/limpiar A01 y su cache entre invocaciones.

Inyectar AccessTokenIssuer real y emitir UserAccount correspondiente a la fila persistida; Authorization: Bearer, sin jwt(), user() ni decoder mock en la matriz. Anónimo no lleva Authorization. Roles con cliente sembrado para filas propio/ajeno, más tests de roles sin cliente. Las 14 rutas × 5 columnas producen 70 combinaciones. Comparar estado de pedidos/stock/productos antes/después de rechazos, types y ausencia de secretos; no conformarse con status. @DisplayName exacto del escenario del delta, nombre de invocación endpoint × rol. Complementar con roles desconocidos/múltiples y pruebas de los proxies.

OrderAuthorizationQueryCountTest y AdminUsersQueryCountTest (*Test*) siembran 120 filas (>50), flush/clear y Statistics.clear antes de medir. Presupuesto: listado de pedidos HTTP ≤2 sentencias (PK cuenta + proyección); listado administrativo ≤2 (página + count); medir también la consulta de identidad, no solo pedidos. Probar cliente y global; conteo independiente del volumen. Tests de repositorio usan BD real. Unidad para cada método público nuevo/modificado de aplicación y cada mapper.

### 6. Impacto exacto en tests existentes

Rutas relativas a src/test/java/com/codeja/master_acdc/. No bajar expectativas de negocio para aceptar 403 ni desactivar filtros.

| Archivo | Hallazgo y estrategia |
|---|---|
| ordering/OrderPlacementIT.java | jwt() y X-Customer-Id; sembrar cuenta/cliente, bearer real, retirar escenarios 400 de cabecera en favor de identidad/permiso, conservar atomicidad y concurrencia |
| ordering/OrderCancellationIT.java | jwt() y cabecera; mismo cambio, pedidos ajenos ligados a otra cuenta, conservar 404/409 y liberación única |
| ordering/infrastructure/adapters/in/web/OrderControllerTest.java | jwt() sin authorities y cabecera; slice con authorities explícitas para prueba de transporte, mock del contexto de cliente, comprobar ausencia de customerId en respuestas; seguridad real en matriz |
| ordering/infrastructure/adapters/in/web/OrderListingIT.java | jwt() sin authorities y listado global; bearer CUSTOMER para filtrado, SUPPORT para escenarios globales/métricas y cliente sin nombre |
| catalog/infrastructure/adapters/in/web/ProductControllerTest.java | jwt() en POST/PUT sin permisos; authorities product:write explícitas en slice, filtros reales |
| catalog/infrastructure/adapters/in/web/CreateProductIT.java | jwt() sin permisos; bearer real CATALOG_MANAGER |
| catalog/infrastructure/adapters/in/web/ProductSearchMongoIT.java | POST de preparación con jwt(); bearer real CATALOG_MANAGER; GET sigue público |
| inventory/infrastructure/adapters/in/web/StockReservationIT.java | jwt() sin permisos; bearer real CATALOG_MANAGER; conservar 201/400/404/409 |
| identity/ApiSecurityTest.java | bearer CUSTOMER real usado en reservas/escrituras y cabecera en pedidos; sembrar/emisor por rol, sustituir contrato temporal de cabecera y conservar tests 401/CSRF/cache/conflictos |
| identity/AuthFlowIT.java | llamadas GET orders con token de login CUSTOMER; conservar flujo y claims exactos, comprobar listado solo propio; no requiere jwt() |
| ordering/application/ListOrdersByStatusServiceTest.java y ordering/infrastructure/adapters/out/persistence/OrderSummaryQueryAdapterIT.java | adaptar criterio/alcance y proyección; revisar también OrderSummaryProfilingIT y llamadas de listado al cambiar firma |
| ordering/application/CreateOrderServiceTest.java, CancelOrderServiceTest.java; OrderRepositoryAdapterIT.java | adaptar comandos/criterios solo si cambia contrato interno, conservar tests unitarios y persistencia |

**Guardianes: tareas exclusivamente humanas**, nombres de scripts/protected-tests.txt:

- catalog/infrastructure/adapters/in/web/CatalogProductEditingMongoIT.java: jwt() sin authorities en POST/PUT → product:write o bearer CATALOG_MANAGER; conservar conflictos y versión.
- catalog/infrastructure/adapters/in/web/CatalogProductCacheEditingIT.java: igual para preparación/edición; conservar invalidación y lecturas públicas.
- inventory/infrastructure/adapters/in/web/StockReservationConcurrencyTest.java: ya usa AccessTokenIssuer real pero emite CUSTOMER; cambiar a CATALOG_MANAGER. Conservar 50 solicitudes, 30/20 y timeout 1,5–3,5 s.
- ordering/infrastructure/adapters/out/persistence/OrderSummaryQueryAdapterQueryCountTest.java: firma actual sin alcance; la persona debe aprobar y adaptar el criterio de lectura global conservando 120 filas y ≤2, antes de la rotura de firma.

Los otros 16 guardianes del manifiesto se ejecutan intactos: StockReservationTransactionIT, ArchitectureTest, CatalogArchitectureTest, PersistenceRulesTest, DomainSignatureRulesTest, ProductEditingMongoRepositoryAdapterTest, CatalogSearchMongoQueryCountTest, ProductSearchPaginationMongoTest, ProductDetailQueryCountMongoTest, CatalogMongoStructureIT, CatalogMongoArchitectureTest, CacheTtlPolicyTest, ProductDetailCacheTest, ProductDetailCacheRedisIT, ProductCacheMultiInstanceIT y CatalogStockProjectionCacheInvalidationIT. Si aparece otra incompatibilidad, detener apply y pedir decisión, sin relajar guardianes.

## Risks / Trade-offs

- [Activar permisos rompe guardianes existentes] → coordinar sus cuatro cambios humanos antes de activar RBAC o cambiar firmas; ninguna excepción permanente ni sobrecarga evasiva.
- [Filtrado null abre acceso global] → alcance explícito y tests de cuenta sin cliente; global solo tras autoridad comprobada.
- [CGLIB no intercepta final o lambda] → decorador no final registrado como bean y prueba de invocación denegada real.
- [Rollback a versión que desconoce REFUNDED] → no ejecutar versión anterior sobre esos pedidos; preferir corrección hacia delante. No convertir estados ni reabrir API con cabecera.
- [Specs previas aún activas] → una persona sincroniza add-authentication antes y reconcilia sus requisitos temporales; este workflow no archiva ni sincroniza. Dos nombres históricos de escenarios de cabecera se conservan por trazabilidad del validador, con pasos actualizados a permisos.

## Migration Plan

1. Revisar propuesta y concretar las adaptaciones humanas de guardianes; no empezar cambio de firma bloqueado.
2. Implementar roles, conversor, puente y decoradores junto con fixtures; después consultas, respuestas, reembolso y usuarios. No requiere nueva migración para REFUNDED ni customer_id (V8 ya existe).
3. Avisar a consumidores del breaking: eliminar cabecera y customerId de respuestas; usar bearer de cuenta, sin generar cuentas retroactivas para clientes históricos.
4. Ejecutar tests unitarios/matriz/conteos/arquitectura y todos los IT, Spotless, graphify update ., validación OpenSpec y verify fast. Registrar evidencia, sin atribuir PIT 75% a fast.
5. Tras revisión, una persona añade las protecciones del paso 8 para HttpSecurityConfiguration, Role, servicio de tokens y SecurityMatrixTest en hooks/plugin/scripts y decide inclusión de IT en loop. Esto no se ejecuta durante apply de este plan por el agente.
