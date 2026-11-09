# Tasks

Plan pendiente de revisión. Cada escenario usa `@DisplayName("<nombre exacto del escenario>")` de los cinco deltas y métodos `should_<comportamiento>_when_<condición>`. Tests mencionados son previstos, no implementados. Las tareas HUMANA no las ejecuta el agente; son bloqueos para los pasos afectados, no permiso implícito de edición.

## 1. Coordinación de contratos y guardianes

- [x] 1.1 **HUMANA** revisar breaking (incluidas respuestas sin customerId), tabla de permisos sin herencia ADMIN, excepción de comandos internos, siete claims sin sv y dirección identity → ordering.api. Verificación: revisión de proposal/design y aceptación de la matriz de 70 combinaciones de SecurityMatrixTest.
- [x] 1.2 **HUMANA** adaptar CatalogProductEditingMongoIT y CatalogProductCacheEditingIT a product:write/bearer CATALOG_MANAGER. Verificación: ambos guardianes conservan assertions de versión/cache y pasan al activar permisos; diff revisado por la persona.
- [x] 1.3 **HUMANA** cambiar el rol emitido por AccessTokenIssuer en StockReservationConcurrencyTest de CUSTOMER a CATALOG_MANAGER. Verificación: guardián original mantiene 50 reservas, 30/20 y espera 1,5–3,5 s, sin relajar asserts.
- [ ] 1.4 **HUMANA** decidir/adaptar la firma con criterio de alcance de OrderSummaryQueryAdapterQueryCountTest antes de cambiar findByStatus. Verificación: compilación coordinada y guardián con 120 filas y ≤2 intactos; si no hay decisión, detener esa parte de apply, sin sobrecarga de compatibilidad.

## 2. Roles, conversor y fronteras de método

- [x] 2.1 Leer package-info de paquetes afectados e implementar Role inmutable con la tabla exacta. Verificación: RoleTest parametrizado comprueba cada conjunto, unión, ausencia de herencia y permisos desconocidos; documentar la tabla en docs/lab-s12.md.
- [x] 2.2 Implementar conversor conectado al resource server; conservar claims y validadores. Verificación: RolesAndPermissionsConverterTest y JwtTokenAdapterTest cubren roles conocidos/desconocidos/mixtos/vacíos/mal tipados; AuthFlowIT traza «Claims permanecen exactos tras login».
- [x] 2.3 Activar @EnableMethodSecurity, decoradores no final de CreateProductUseCase, EditProductUseCase y ReserveProductStockUseCase, y matcher ADMIN. Verificación: MethodAuthorizationTest invoca beans reales y demuestra denegación antes del delegado; ApiSecurityTest conserva ProblemDetail, CSRF, Basic/Bearer y acceso público. No aplicar stock:reserve al checkout interno.
- [x] 2.4 Adaptar tests no guardianes de catálogo/inventario enumerados en design (ProductControllerTest, CreateProductIT, ProductSearchMongoIT, StockReservationIT) y ApiSecurityTest para permisos de preparación. Verificación: esos tests pasan con filtros activos y contratos de negocio originales; jwt() con authorities solo en slices, bearer real en integración.

## 3. Identidad y autorización de pedidos

- [ ] 3.1 Introducir CurrentOrderActor en ordering.api e implementación en identity.infrastructure con proyección por PK; producir alcance explícito sin cache. Verificación: CurrentOrderActorIT prueba cuenta ausente/null/presente y «Cambio de asociación se observa con el mismo token»; ModuleDependenciesTest e IdentityArchitectureTest acreditan dirección sin ciclo.
- [ ] 3.2 Añadir decoradores de creación/cancelación/lectura que comprueban permiso antes de resolver alcance y delegar; retirar X-Customer-Id y customerId de HTTP. Verificación: MethodAuthorizationTest, OrderControllerTest y OrderPlacementIT cubren «Cuenta sin cliente no crea pedido», «Cabecera antigua no suplanta identidad», creación/fronteras/precedencia del delta y ausencia de customerId en respuestas.
- [ ] 3.3 Extraer conversiones tocadas a OrderWebMapper, OrderPersistenceMapper y mapper de resúmenes, sin conversiones en DTO/entidad/controller. Verificación: tests unitarios de cada mapper y AuthorizationArchitectureTest; actualizar ejemplos HTTP en docs/lab-s12.md.
- [ ] 3.4 Tras 1.4, cambiar listado a criterio estado/alcance, Specification tipada y proyección agregada; actualizar callers ListOrdersByStatusServiceTest, OrderSummaryQueryAdapterIT, OrderSummaryProfilingIT y OrderControllerTest. Verificación: ListOrdersByStatusServiceTest, OrderListingIT y OrderSummaryQueryAdapterIT cubren propios/globales, lista vacía, métricas, líneas vacías, nombre null y errores status originales.
- [ ] 3.5 Añadir GetOrderUseCase/Service y GET detalle con consulta id/alcance y una colección precargada. Verificación: GetOrderServiceTest y OrderDetailIT trazan «Detalle propio y lectura global», «Detalle ajeno o inexistente», «Ausencia de permiso de lectura oculta existencia» y «Cliente ausente no abre el alcance»; rechazos sin cambios.
- [ ] 3.6 Limitar siempre la lectura/update condicional de cancelación al id y cliente de la cuenta, incluso con order:read-any. Verificación: CancelOrderServiceTest, OrderCancellationIT y OrderRepositoryAdapterIT conservan los escenarios de cancelación, concurrencia y liberación única; «Usuario multirrol no puede cancelar un pedido ajeno» demuestra que CUSTOMER+SUPPORT recibe 404 sin modificar pedido ni stock; SUPPORT solo conserva 403.
- [ ] 3.7 Adaptar OrderPlacementIT, OrderCancellationIT, OrderListingIT, ApiSecurityTest y AuthFlowIT a cuentas/bearer, eliminando contratos 400 de cabecera. Verificación: esas suites mantienen atomicidad, validaciones, 404 ajeno y claims; los dos nombres históricos de precedencia usan pasos nuevos del delta sin pretender cabecera obligatoria.
- [ ] 3.8 Añadir OrderAuthorizationQueryCountTest fast con 120 pedidos, flush/clear/Statistics.clear, datos propios/ajenos. Verificación: ≤2 sentencias por listado HTTP (incluida PK de cuenta), para CUSTOMER y SUPPORT, sin depender del número de filas; guardián humano de conteo en verde.

## 4. Reembolso atómico

- [ ] 4.1 Añadir REFUNDED, regla de dominio, RefundOrderUseCase/Service y puerto/update condicional dentro de TransactionTemplate. Verificación: OrderTest y RefundOrderServiceTest cubren PAID/SHIPPED y todos los rechazos, sin llamada a stock ni evento de cancelación; todo método público nuevo tiene test.
- [ ] 4.2 Exponer POST refund con decorador order:refund, mapper y ProblemDetail order-not-refundable. Verificación: OrderRefundIT traza «Reembolso desde PAID o SHIPPED», «Reembolso rechazado por estado», «Reembolso de pedido inexistente» y ausencia de cambios de stock; OrderCancellationIT cubre «Pedido reembolsado no cancelable».
- [ ] 4.3 Probar dos reembolsos y carrera cancelación/reembolso con transacciones independientes PostgreSQL. Verificación: OrderRefundConcurrencyIT traza «Reembolsos concurrentes no repiten transición» y «Cancelación y reembolso concurrentes», un único 200, 409 correcto y stock conforme al ganador. Documentar estados y ausencia de API de pago/envío en docs/lab-s12.md.

## 5. Consulta administrativa

- [ ] 5.1 Implementar ListUsersUseCase/Service, puerto/proyección paginada mínima y mappers dedicados. Verificación: ListUsersServiceTest, UserSummaryPersistenceMapperTest y AdminUserWebMapperTest comprueban campos permitidos y límites page/size, sin entidades/hash/refresh.
- [ ] 5.2 Añadir endpoint y decorador user:read con matcher hasRole ADMIN ya instalado. Verificación: AdminUsersIT traza los cinco escenarios de user-administration, sin secretos en JSON; MethodAuthorizationTest prueba ADMIN sin permiso y permiso sin rol para demostrar ambos controles.
- [ ] 5.3 Añadir AdminUsersQueryCountTest fast con 120 cuentas, flush/clear/Statistics.clear y presupuesto ≤2. Verificación: paginación estable por id, totalElements real, última página/vacía, sin N+1; documentar contrato de paginación en docs/lab-s12.md.

## 6. Matriz y reglas de arquitectura

- [ ] 6.1 Implementar SecurityMatrixTest parametrizado, 14 rutas × 5 roles, con @DisplayName("Matriz de acceso con recursos válidos") y nombres de invocación endpoint × rol. Verificación: 70 combinaciones con AccessTokenIssuer real, cuentas persistidas, fixtures propias por fila, propio/ajeno, A01 válido, versiones y stocks correctos; asserts de status/type/estado antes-después. Documentar desviación de A1.
- [ ] 6.2 Añadir escenarios complementarios de authorization: roles desconocidos/mixtos, ADMIN sin create/cancel y denegación antes de consultas. Verificación: SecurityMatrixTest y MethodAuthorizationTest trazan los escenarios restantes del delta, incluido «Lectura global no concede cancelación»; los espías de delegación no sustituyen tests de queries reales.
- [ ] 6.3 Añadir ModuleDependenciesTest y AuthorizationArchitectureTest sin editar guardianes: ausencia de ciclos, pureza de capas, comandos HTTP decorados y ausencia de entrada customerId. Verificación: nuevos ArchUnit más IdentityArchitectureTest y todos los guardianes de arquitectura intactos; documentar dirección de módulos y alcance de la regla en docs/lab-s12.md.

## 7. Verificación integrada y entrega

- [ ] 7.1 Ejecutar `bash ./mvnw spotless:apply`, `graphify update .`, `openspec validate add-rbac-and-object-authorization --strict --no-interactive` y `bash ./verify-quality.sh --fast`; pegar salida completa. Verificación: tests fast/matriz/conteos/ArchUnit en verde y ningún hallazgo nuevo, guardianes solo con cambios humanos revisados.
- [ ] 7.2 Ejecutar `bash ./mvnw -q verify` para IT y contrastar trazabilidad de todos los escenarios y contratos conservados; documentar evidencia en docs/lab-s12.md. Verificación: AuthFlowIT, suites de pedidos/admin, carreras y guardianes de catálogo/cache en verde; no declarar PIT medido por fast ni cambiar el umbral 75% o configuración protegida.

## Workflow follow-up

- **HUMANA, paso 8 posterior:** proteger HttpSecurityConfiguration (SecurityConfig del enunciado), Role, servicio de tokens y SecurityMatrixTest en hooks/plugin/scripts; comprobar manualmente la protección y la selección de tests. No editar esos mecanismos en este cambio por el agente.
- add-authentication ya está archivado. Una persona revisa la eliminación del requisito heredado de cabecera y los deltas de permisos de este change antes de gestionar su sincronización/archive; el agente no ejecuta ninguno.
- add-session-version-revocation incorpora sv y revocación inmediata por separado.
