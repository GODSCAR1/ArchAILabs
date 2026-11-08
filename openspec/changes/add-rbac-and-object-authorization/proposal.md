# Proposal

## Why

La autenticación ya implementada no impide escribir catálogo con CUSTOMER ni suplantar al propietario de un pedido mediante X-Customer-Id. El paso 5 y la matriz del paso 7 de S12 cierran esas fronteras con permisos y consultas acotadas por identidad.

## What Changes

- Role con la tabla propuesta, sin ajustes: CUSTOMER (`order:create`, `order:read-own`, `order:cancel-own`); CATALOG_MANAGER (`product:write`, `stock:reserve`); SUPPORT (`order:read-any`, `order:refund`); ADMIN (`product:write`, `stock:reserve`, `order:read-any`, `order:refund`, `user:read`). ADMIN no hereda CUSTOMER.
- Conversor de roles conocidos a ROLE_<rol> y permisos; roles desconocidos no conceden authorities. Decoradores de infraestructura con autorización de método, probada a través del proxy real.
- **BREAKING**: retirar X-Customer-Id y customerId del contrato HTTP de pedidos, incluidas respuestas. Resolver el cliente desde sub mediante consulta por PK a identity en cada petición, sin cache ni claims nuevos.
- Listado, nuevo detalle y cancelación filtran propiedad en consulta, salvo `order:read-any`; este permiso no concede por sí solo crear ni cancelar. Sin permiso: 403; objeto fuera de alcance: 404.
- Nuevo reembolso PAID/SHIPPED → REFUNDED, 200; estados restantes → 409; sin efectos en stock.
- Nuevo listado paginado de usuarios: permiso `user:read` y rol ADMIN en la cadena, sin contraseñas ni datos de refresh.
- SecurityMatrixTest parametrizado con bearer real del AccessTokenIssuer del contexto, datos aislados por fila y SKU válido `A01` en lugar de `A1`.

## Non-goals

- Claim sv y revocación inmediata: pertenecen a add-session-version-revocation. Conservar exactamente los siete claims actuales iss, sub, aud, iat, exp, jti, roles.
- Nuevos permisos implícitos para ADMIN, API de pago/envío, pasarela de reembolso monetario o cambios de stock al reembolsar.
- Cambiar cache, dependencias, hooks, plugin, scripts o protecciones del paso 8. La protección posterior es humana.

## Capabilities

### New Capabilities

- `authorization`: permisos, fronteras HTTP y matriz de acceso por rol; complementa api-security del change previo todavía no archivado.
- `user-administration`: consulta administrativa paginada y mínima de cuentas.

### Modified Capabilities

- `ordering`: identidad derivada de la cuenta, retirada de cabecera/identificador público, propiedad en consultas, detalle y reembolso.

## Impact

- identity: Role, conversor, HttpSecurityConfiguration (nombre real del SecurityConfig del enunciado), resolución de cliente, proyección administrativa. ordering: controlador, mappers, casos de uso y consultas; catalog/inventory: decoradores de comandos. Sin dependencias nuevas.
- Evitar el ciclo manteniendo identity → ordering.api: un contrato de contexto de cliente en ordering.api será implementado desde identity.infrastructure; ordering consume su propia interfaz. ArchUnit comprobará ausencia de ciclos de módulos y pureza de capas.
- Inventario de tests y tareas humanas detallado en design.md. Guardianes directamente afectados: CatalogProductEditingMongoIT, CatalogProductCacheEditingIT, StockReservationConcurrencyTest y OrderSummaryQueryAdapterQueryCountTest si se cambia la firma de consulta. Ninguno se adapta por el agente ni mediante sobrecargas evasivas.
- El contexto protegido de OpenSpec está desactualizado (sin autenticación, cabecera obligatoria). Los requisitos de cabecera de ordering y el delta temporal de add-authentication se sustituyen explícitamente aquí; no se editan specs principales ni el change previo. La persona deberá reconciliar su orden de sincronización.
- Conflictos del enunciado resueltos según decisiones existentes: decoradores en infraestructura, no servicios Spring en application; JWT sin sv hasta el tercer change; A1 se sustituye porque viola Sku. ADR 0007/0009/0010 conservados (el código ya retiró catálogo PostgreSQL).
- «Cada comando» se aplica a los comandos HTTP de negocio: imponer stock:reserve a la reserva interna del checkout impediría comprar a CUSTOMER. Los comandos internos/eventos y los flujos de autenticación conservan su contrato; design enumera expresamente esta frontera para revisión.
- Rama lab/s12, ningún hallazgo abierto permitido. Umbral PIT indicado 75%; fast no acredita PIT y el change anterior documenta que aún no está configurado. No introducir ni bajar configuración para medirlo.

Planificación únicamente: revisión humana y nueva petición explícita de apply antes de implementar.
