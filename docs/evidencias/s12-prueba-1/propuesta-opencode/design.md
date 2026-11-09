# Design

## Context

Ver `proposal.md` para motivación y alcance. Hallazgos confirmados en código:

- `Order` y `OrderLine` guardan estado, producto y cantidad, sin importe ni operación de pago.
- `OrderJpaRepository` usa UPDATE condicional para cancelación y reembolso. `OrderingConfiguration` envuelve casos de uso con `TransactionTemplate`.
- `CurrentOrderActor` permite resolver la cuenta desde identity por su API pública. `OrderActorResolver.readingScope()` concede alcance global con `order:read-any`: no sirve para facturas.
- `HttpSecurityConfiguration` ya protege `/api/**`; no necesita modificación. `Role` carece de `invoice:read-own` y está protegido.
- `SecurityMatrixSupport` siembra PAID directamente, limpia pedidos y usa `order-not-found` como 404 genérico; su ampliación requiere intervención humana, no eliminar sus comprobaciones.
- `AuthorizationArchitectureTest` comprueba capas puras, API entre módulos, DTO sin customerId y mappers estáticos. Leer los `package-info.java` de cada paquete antes de añadir clases en implementación.

## Goals / Non-Goals

**Goals:** mantener emisión y cambio de estado en una transacción PostgreSQL, lectura con propietario obligatorio y coste constante por página.

**Non-Goals:** ver `proposal.md`. No se crea un subsistema asíncrono de facturación ni se declara un pago recibido a partir de un GET.

## Decisions

### 1. Facturas dentro de ordering

Añadir `Invoice` como record de dominio validado con cinco campos: UUID id, String number, UUID orderId, BigDecimal totalAmount e Instant issuedAt. Entidad `InvoiceJpaEntity` y migración siguiente a V8 con tabla `invoices`: PK id, UNIQUE number, UNIQUE order_id, FK order_id → orders sin borrado en cascada, total_amount NUMERIC(19,2) NOT NULL CHECK >= 0 e issued_at TIMESTAMPTZ NOT NULL. Todos los campos obligatorios. La propiedad se obtiene mediante join al pedido, sin duplicar customer_id ni cargar entidades o líneas del pedido.

Índices de lectura: `orders(customer_id,id)` e `invoices(issued_at DESC,id ASC)`, además de los índices únicos. Comprobar antes los índices existentes para no duplicarlos. No crear asociación JPA eager; bastan identificadores escalares. No añadir FK hacia customers, pues existen pedidos históricos con cliente ausente.

Alternativa: módulo billing y evento posterior al commit. Se descarta porque introduce consistencia eventual y recuperación de eventos para una única entidad asociada al pedido.

### 2. Confirmación interna, atómica e idempotente

Crear `ConfirmOrderPaymentUseCase` y `ConfirmOrderPaymentService`, puertos de dominio para actualización condicional y facturas. Entrada agrupada en un record con orderId y totalAmount; validar UUID no nulo e importe exacto entre 0 y 99999999999999999.99, sin redondeo implícito (`setScale(2, UNNECESSARY)`). `Clock` inyectado para issuedAt. Número `INV-` seguido del UUID de factura: único y no fiscal/correlativo.

El caso de uso ejecuta un UPDATE parametrizado PENDING → PAID. Si afecta una fila, genera y guarda factura. Si afecta cero, consulta pedido y factura: solo PAID con factura y mismo importe es reintento válido; devuelve la original. Importe discrepante, estados incompatibles, PAID histórico sin factura e inexistencia se rechazan con excepciones de dominio concretas. No hay contrato HTTP para esos errores internos. PostgreSQL serializa los UPDATE concurrentes sobre el mismo pedido: el perdedor observa el commit del ganador antes de leer su factura. UNIQUE(order_id) refuerza el invariante, no sustituye la idempotencia.

`TransactionTemplate` en `ordering.infrastructure.config` engloba UPDATE y INSERT; cualquier error de inserción revierte PAID. Una cancelación puede ejecutarse después del pago, dado que PAID sigue siendo cancelable: se conserva factura y se libera stock según el flujo existente. Si la cancelación gana antes, el UPDATE de pago no afecta filas y no factura. No añadir listeners AFTER_COMMIT para emisión: dejarían un pedido PAID sin factura si fallan. No modificar firmas existentes del dominio para añadir precio ni constructores de compatibilidad.

El importe llega de un futuro adaptador interno confiable que confirma el pago. No se añade dicho productor ni un webhook: este repositorio aún no tiene integración de pagos. La propuesta fija esta entrada para revisión; no inventa precios a partir de cantidades ni consulta la cache del catálogo.

### 3. Consultas y contrato JSON

Crear `ListInvoicesUseCase`/`ListInvoicesService` y `GetInvoiceUseCase`/`GetInvoiceService`, con puertos en domain para lectura y records propios para página/criterio (sin Pageable de Spring en aplicación). Adaptadores JPA con proyecciones, metamodelo y `Specification.allOf` para id y propietario; mismo filtro de propietario en página y count. Listado: dos consultas (página y count), detalle: una; no consultas por elemento ni carga de líneas.

`InvoiceController` expone los dos GET solicitados. `InvoiceWebMapper` y `InvoicePersistenceMapper` serán final, sin estado y con métodos estáticos, probados por separado. Respuesta de detalle e ítems: `{id, number, orderId, totalAmount, issuedAt}`. Página: `{content, page, size, totalElements, totalPages}`. Orden fijo por issuedAt DESC e id ASC; no admitir orden arbitrario. Validar page y size antes de construir offset, calculándolo con long para evitar overflow; page entero no negativo, size 1..100, por defecto 0/20. totalAmount es número JSON decimal e issuedAt ISO-8601 UTC.

Errores de paginación: 400 `invalid-invoice-pagination`; detalle inválido/inexistente/ajeno: 404 `invoice-not-found`, ambos bajo `https://api.master-acdc.dev/problems/`. Handler de infraestructura con ProblemDetail. No convertir errores de infraestructura en listas vacías o 404.

### 4. Autorización y zonas protegidas

Proponer `invoice:read-own` solo para CUSTOMER. Decoradores de infraestructura no final con `@PreAuthorize("hasAuthority('invoice:read-own')")` protegen ambos casos de uso antes de validar parámetros o consultar facturas. Resolver el cliente con `CurrentOrderActor` por petición; usar un alcance propio obligatorio o explícitamente vacío, nunca null como comodín ni `readingScope()` global de pedidos. Los decoradores reemplazan cualquier alcance suministrado por el llamador. Para cuenta sin cliente: página vacía y detalle 404.

La matriz propuesta está en `specs/authorization/spec.md`. Una persona deberá ampliar `Role` y `SecurityMatrixTest`/`SecurityMatrixSupport`, incluidos datos propios/ajenos, comprobación del type por recurso, snapshots de facturas y limpieza antes de borrar pedidos con FK. Preservar todas las filas, rechazos y aserciones anteriores. No sustituir esta ampliación por un test paralelo para eludir el guardián. La implementación de seguridad queda bloqueada hasta esa intervención/autorización explícita; la revisión de la propuesta no es permiso implícito para editar zonas protegidas.

Alternativa: reutilizar `order:read-own`. Se descarta por mezclar recursos distintos y permitir concesiones involuntarias futuras. No se amplía `order:read-any` ni se usa hasRole para facturas. No hay claims nuevos ni cambios de JWT, sesión, CSRF o validación de versiones.

## Reglas de arquitectura

Comprobar con ArchUnit al implementar, sin atribuir a los guardianes actuales reglas que aún no cubren:

- `ordering.application` solo depende de su dominio y biblioteca estándar; domain sin Spring, JPA o Jackson.
- Facturación permanece en ordering: no depende de catalog ni de identity internals. El adaptador existente `identity.infrastructure.adapters.out.ordering` implementa `ordering.api.CurrentOrderActor`.
- El acceso de ordering a inventory sigue limitado a `inventory.api` desde infraestructura; ni emisión ni consulta de facturas necesitan inventory.
- Puertos de dominio solo interfaces; dependencias de persistencia y seguridad solo en infraestructura. Mappers únicamente de infraestructura, estáticos y sin estado; decoradores web no final.
- Añadir `InvoiceArchitectureTest` para relaciones nuevas y conservar intactos los guardianes existentes. Si un guardián requiere otra firma o diseño, detenerse y pedir decisión humana.

## Risks / Trade-offs

- [No hay productor de pagos ni precios en pedidos] → confirmar en revisión la entrada de importe interno; no presentar el flujo como pago HTTP operativo.
- [Numeración técnica sin correlación fiscal ni soporte multidivisa] → alcance mínimo explícito: una única unidad monetaria del negocio; acordar otro contrato antes de soportar múltiples divisas o requisitos fiscales.
- [Lecturas de facturas tras cancelación/reembolso] → conservar documento original; no generar abonos en este cambio.
- [Page y count bajo modificaciones concurrentes] → orden determinista sobre datos estables; no prometer snapshot entre peticiones ni paginación de cursor.
- [FK afecta fixtures y limpieza] → pruebas de migración y revisión humana de matriz; adaptar fixtures no protegidas sin fabricar facturas para PAID históricos.

## Migration Plan

1. Revisión humana de propuesta, importe, numeración técnica y delta de permisos; intervención en zonas protegidas antes de aplicar seguridad.
2. Migración aditiva de tabla, restricciones e índices. Sin backfill: los PAID anteriores siguen sin factura y no se reparan mediante GET ni reintento de pago.
3. Desplegar casos de uso internos y consultas; verificar atomicidad, concurrencia, matriz y coste constante con PostgreSQL real mediante Testcontainers.
4. Rollback de aplicación conservando tabla y facturas emitidas; no eliminar documentos ni revertir estados de pago automáticamente. Nunca bajar umbrales ni debilitar guardianes para desplegar.

## Verification

Tests unitarios de todos los métodos públicos de casos de uso; integración PostgreSQL de emisión, rollback, unicidad y carreras reales. Tests MVC con `@MockitoBean` para contrato y un test de consultas `*Test` en fast loop con 120 facturas propias y ajenas, flush/clear y Statistics.clear: máximo 2 SQL en adaptador paginado y 1 en detalle. Medir por separado el endpoint: máximo 3 SQL incluyendo resolución de cuenta, con versión de sesión ya presente en Redis. Probar página inicial e intermedia y totalElements filtrado.

Los escenarios tendrán `@DisplayName` exactamente igual al texto de su especificación y métodos `should_<comportamiento>_when_<condición>`. Ejecutar Spotless, `bash ./verify-quality.sh --fast` y, durante implementación, PIT manteniendo el 75 % vigente con la configuración existente. Actualizar el grafo solo tras cambios de código; este change contiene únicamente planificación.
