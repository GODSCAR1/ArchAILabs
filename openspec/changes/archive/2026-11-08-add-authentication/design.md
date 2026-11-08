# Design

## Context

Motivación y recorte del lab en `proposal.md`. Se han leído AGENTS.md, los ADR 0007/0009/0010 y `docs/lab-s12-enunciado.md`, incluidas las decisiones de Oscar. La rama indicada es lab/s12, con PIT 75%. Este documento es planificación, no permiso para editar zonas protegidas.

Hallazgos comprobados:

- No existe SecurityFilterChain propia en src/main. El diff local del usuario en pom.xml ya añade los starters security, oauth2-resource-server y security-test; no se toca.
- V4 crea `customers(id UUID, name VARCHAR(120) NOT NULL)`. CustomerJpaEntity/Repository pertenecen a ordering.infrastructure; no hay caso de uso público para crear clientes. V7 ya retiró catálogo PostgreSQL; V8 es la migración prevista para identidad.
- OrderingConfiguration ya ensambla casos de uso con TransactionTemplate. ArchitectureTest protege application de ordering/inventory y limita inventory.api a ordering.infrastructure; PersistenceRulesTest exige locks y timeout para findForUpdate* de repositorios.
- Los tests HTTP se detallan abajo. Los tests de cache arrancan PostgreSQL/Mongo/Redis; otros tests JPA usan PostgreSQL. No se presupone una fixture PostgreSQL/Redis global que no existe: reutilizar los patrones @ServiceConnection y compartir los contenedores de los nuevos tests de identidad.
- Las specs existentes describen resultados de negocio sin precondición de autenticación. Los tres deltas añaden explícitamente esa precondición; errores de autenticación preceden a errores de negocio. No se reescriben ni sincronizan specs principales.

## Goals / Non-Goals

**Goals:** transacciones demostrables de registro y familias; tokens verificados de verdad por kid; pruebas de seguridad reales además de tests de negocio con jwt(); dependencias hexagonales explícitas.

**Non-Goals:** cambios de consultas/stock/cache, importar Security en application, filtros de versión de sesión o roles de API. El punto de extensión futuro es el puerto emisor de tokens y la composición de validadores del decoder, sin emitir sv ni hacer lecturas Redis ahora.

## Decisions

### 1. Módulo y frontera de clientes

Crear `identity/domain/model`, `identity/domain/port`, `identity/application/port`, `identity/application` e infraestructura `adapters/in/web`, `adapters/out/persistence`, `adapters/out/security`, `adapters/out/ordering`, `config`. Leer package-info existentes y añadir los del módulo nuevo antes de sus clases.

Casos de uso Register, Login, Refresh, Logout y ChangePassword: interfaces `<Accion>UseCase` y servicios puros. Puertos mínimos para cuentas/familias, encoder de contraseña, política de contraseñas comunes, emisor de access tokens y creación de cliente. Clock de Java inyectado para vencimientos. Agrupar comandos/dependencias que superen cinco parámetros en records, no constructores de compatibilidad. Secretos nunca se vuelcan por toString/logging.

Introducir una fachada pequeña `ordering.api.CustomerRegistrationFacade` que devuelva UUID, implementada en infraestructura de ordering con su CustomerJpaRepository existente. Si necesita lógica de aplicación, encapsularla con `RegisterCustomerUseCase/Service` y puerto de dominio propio; application de ordering no depende de identity. `identity.infrastructure.adapters.out.ordering` implementa el puerto de creación de cliente contra esa fachada. La transacción de identity y la escritura del cliente participan en el mismo manager PostgreSQL (REQUIRED, nunca REQUIRES_NEW). Se evita importar CustomerJpaEntity desde identity o duplicar su mapeo.

Mapeos en clases finales estáticas dedicadas IdentityWebMapper, UserAccountPersistenceMapper y RefreshTokenPersistenceMapper, con tests; la nueva frontera de clientes sigue el mismo patrón. Los beans ensamblan, no contienen reglas de negocio.

Alternativas descartadas: SQL directo a customers desde identity (propiedad difusa), mover customers de módulo (innecesario), persistencia cruzada fuera de la transacción (cliente huérfano).

### Reglas de arquitectura

Añadir IdentityArchitectureTest sin modificar los guardianes actuales:

- identity.domain no depende de Spring, JPA, Jackson 2/3 ni infraestructura de ningún módulo.
- identity.application solo depende de Java y de identity.application/domain; sin Security ni @Transactional.
- Puertos de salida solo interfaces de identity.domain.port; implementaciones en infraestructura. Security/JWK/Nimbus/PasswordEncoder únicamente en identity.infrastructure.
- identity.infrastructure puede consumir ordering.api para registro; nunca ordering.infrastructure/domain/application ni inventory.api ni catalog.
- ordering.api no depende de identity ni de frameworks; su implementación reside en ordering.infrastructure. ordering e inventory mantienen sus dependencias existentes, sin una dependencia inversa a identity.
- Mappers pertenecen a infraestructura, son finales y sin estado; DTOs/entidades no convierten. Persistencia de identidad conserva asociaciones LAZY o UUID escalares y no expone entidades.
- Se mantienen ArchitectureTest, CatalogArchitectureTest, CatalogMongoArchitectureTest, PersistenceRulesTest y DomainSignatureRulesTest. Si una firma rompe un guardián se detiene apply para decisión humana.

### 2. Esquema V8 y validación

`V8__create_identity.sql` crea:

| Tabla | Campos y restricciones |
|---|---|
| user_account | id UUID PK; email NOT NULL UNIQUE y CHECK email = lower(email); password_hash NOT NULL; roles NOT NULL (colección pequeña de nombres, propuesta TEXT[]); enabled NOT NULL; session_version BIGINT NOT NULL DEFAULT 0; customer_id UUID NULL UNIQUE REFERENCES customers(id) |
| refresh_token | id UUID PK; family_id UUID NOT NULL; user_id UUID NOT NULL FK user_account; token_hash CHAR(64) NOT NULL UNIQUE; expires_at, created_at, family_expires_at TIMESTAMPTZ NOT NULL; rotated_at, revoked_at TIMESTAMPTZ NULL; índices family_id y user_id |

Guardar family_expires_at repetido, inmutable e igual en toda la familia evita buscar el primer token para conocer el tope. expires_at = min(created_at + 7 días, family_expires_at); familia iniciada en login +30 días. Mantener los tokens rotados para detectar replay; no añadir purga en este change.

Normalizar email con Locale.ROOT y validarlo; registro no recibe roles/enabled/customerId. name omitido deriva de la parte local normalizada, explícito debe ser no blanco y ≤120; no truncar silenciosamente. CUSTOMER y enabled=true los decide el servidor. Registro y alta de cliente hacen commit juntos; una colisión UNIQUE se traduce fuera de la transacción fallida a 409 sin dejar cliente huérfano.

PasswordEncoderFactories.createDelegatingPasswordEncoder(), bcrypt por defecto. Cargar `src/main/resources/common-passwords.txt` una vez; usar la misma política en registro y cambio. No hardcodear la lista. El encoder no debe truncar contraseñas silenciosamente: las limitaciones de entrada del bcrypt utilizado se validan y traducen a invalid-password, con test de frontera, sin cambiar el algoritmo por cuenta propia.

Generar el hash ficticio de login una vez al construir el adaptador con el mismo encoder/coste. Login desconocido hace matches contra ese hash; incorrecto contra el real. Comparar cuerpo HTTP idéntico y verificar una operación matches en ambos caminos con tests de adaptador, sin sleeps artificiales ni aserciones frágiles de milisegundos en CI. Cuenta disabled devuelve el mismo error y no obtiene familia.

### 3. Transacciones, locks y revocación confirmada

Refresh opaco de 32 bytes aleatorios criptográficos, codificado base64url; SHA-256 hexadecimal persistido. Solo cookie contiene el secreto, nunca logs, cuerpos de respuesta ni BD.

En infraestructura/config, decoradores con TransactionTemplate envuelven registro, login, refresh, logout y cambio de contraseña. Para evitar carreras con revocaciones masivas, las mutaciones de familias serializan primero por usuario (lock pesimista de cuenta) y después por token. Una lectura inicial por hash obtiene únicamente el userId; tras bloquear cuenta se relee el token con SELECT FOR UPDATE por hash y se valida su estado actual. Todos los findForUpdate* de repositorios llevan @Lock(PESSIMISTIC_WRITE) y hint jakarta.persistence.lock.timeout. Orden de locks único evita inversiones. No se hace bcrypt bajo lock en el camino desconocido de login; el login existente verifica el estado final antes de crear familia.

Refresh válido: validar enabled, vencimientos y revocación; marcar rotated_at y persistir sucesor con la misma familia dentro de una transacción. Reutilización: revocar todas las filas de la familia con una actualización parametrizada, incluyendo sucesor; devolver un resultado de dominio rechazado que permita COMMIT. Solo después del commit el adaptador HTTP traduce ese resultado a 401. Lanzar una excepción runtime dentro de TransactionTemplate para replay revertiría la revocación y está descartado.

Logout: localizar la familia incluso desde un token rotado, revocarla y borrar cookie; familia ya revocada es idempotente 204. Desconocido/ausente es 401 tras CSRF. Cambio de contraseña: comprobar currentPassword, actualizar hash y revocar por user_id en la misma transacción. Login, refresh y cambio comparten lock de cuenta para que una carrera no cree sucesores con credenciales antiguas después del cambio.

Dos refresh iguales concurrentes: uno confirma 200, el otro ve rotated_at y confirma revocación antes de su 401. El refresh entregado al ganador queda invalidado al terminar ambos: es la consecuencia intencional de detección de replay, no dos sesiones válidas. Access tokens ya emitidos no se revocan todavía.

Timeout de bloqueo conserva una espera acotada (propuesta 2 s como stock), se traduce a 503 `https://api.master-acdc.dev/problems/authentication-busy` con Retry-After: 1 y rollback; probarlo separadamente de la carrera de dos refresh, cuya sección crítica no debe agotar el timeout. No quitar lock/hint para conseguir un test verde.

### 4. Claves, JWT y configuración

Cargar una RSA privada CRT PKCS#8 PEM de 2048 bits desde la propiedad canónica `shop.jwt.private-key`, alimentada por `SHOP_JWT_PRIVATE_KEY` desde el entorno mediante el binding de configuración y el enlace explícito del bloque de propiedades de la tarea 1.2. Derivar n/e públicos y kid mediante computeThumbprint RFC 7638. Validar formato/tamaño y fallar al arrancar si faltan secretos de producción. Añadir *.pem a .gitignore, nunca generar claves en src/main/resources.

Un único JWKSource alimenta NimbusJwtEncoder y el procesador de NimbusJwtDecoder mediante un selector de clave que use realmente kid y RS256. No usar withPublicKey ni decoder por HTTP hacia el JWKS propio. El endpoint serializa exclusivamente toPublicJWK; rechazar kid ausente/desconocido en el contrato de tokens emitidos por esta API. Tests deben cambiar kid conservando la misma firma de payload para detectar decoders que ignoran kid (volver a firmar con la clave válida y header distinto).

Propiedades validadas `shop.jwt.issuer=https://api.master-acdc.dev` (sin barra final) y `shop.jwt.audience=master-acdc-api` alimentan emisor y validadores sin quitar/añadir barra final. Claim aud con el valor configurado y decoder que comprueba pertenencia exacta; iss igualdad exacta. Timestamp validator con Clock compartido y política explícita de vencimiento; pruebas con reloj fijo en exp, sin tolerancias implícitas que alarguen la vida contratada de 15 minutos. Claims se construyen explícitamente, no copiando principal/claims entrantes. jti UUID nuevo en cada emisión. roles son nombres, sin permisos ni customerId.

Configuración de tests: nueva `GlobalIdentityTestExtension`, autodetectada vía `src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension`, siguiendo GlobalMongoTestContainerExtension; `junit.jupiter.extensions.autodetection.enabled=true` ya existe. Inicialización sincronizada una sola vez por JVM, antes de cargar contextos: generar RSA 2048, escribir privada PKCS#8 en PEM temporal fuera del repositorio y fijar propiedades de sistema `shop.jwt.private-key` (ruta temporal), `shop.jwt.issuer`, `shop.jwt.audience`, `shop.ops.user` y `shop.ops.password-hash` (hash {bcrypt} generado para el usuario de prueba). Usar los valores decididos de issuer/audience. Mantener el PEM durante toda la JVM y limpiarlo al finalizar; no recrear clave por clase de test.

Los contextos Spring y los arrancados con SpringApplicationBuilder, incluidos ambos de ProductCacheMultiInstanceIT, leen esas propiedades y ejercitan el mismo cargador de PEM que producción. No se importan configuraciones efímeras por contexto ni se introduce fallback de test en src/main. AuthFlowIT y los tests que firman bearer reales obtienen el encoder del contexto o leen el mismo PEM. Los tests de mal issuer/aud/exp/firma usan encoder real y Authorization, no jwt(). Verificar autodetección, inicialización anterior al contexto y reutilización de ruta/clave entre contextos con GlobalIdentityTestExtensionTest y JwtConfigurationTest.

### 5. Cadenas y errores

| Orden | Matcher | Autenticación y política |
|---|---|---|
| 1 | EndpointRequest.toAnyEndpoint() | health permitAll; resto ROLE_OPS con httpBasic, proveedor de usuario OPS propio, sin resource server/bearer |
| 2 | /.well-known/jwks.json | GET público, otros métodos denegados; sin sesión |
| 3 | /api/** | STATELESS, oauth2ResourceServer jwt; GET products/categories y cuatro POST auth públicos; resto authenticated |

Limitar permitAll por método y ruta exacta; no abrir `/api/auth/**` indiscriminadamente ni POST de reservas por estar bajo products. No agregar hasRole en la API ni method security. No aceptar Basic de OPS en la API; no compartir el proveedor OPS con resource server. Las cadenas son servlet-only para no romper contextos no web. Rutas fuera de estas fronteras no reciben nuevos endpoints en este change.

CSRF requiere protección solo en POST refresh/logout. CookieCsrfTokenRepository.withHttpOnlyFalse emite XSRF-TOKEN con Path=/, Secure y SameSite Strict para que la SPA pueda leerla mediante document.cookie desde otra ruta; handler que acepte el token crudo en X-XSRF-TOKEN (no requerir XOR). refresh_token conserva HttpOnly, Secure, SameSite Strict y Path=/api/auth. En login exitoso cargar explícitamente el DeferredCsrfToken para materializar cookie; no basta con configurar el repositorio. Si la estrategia de autenticación borra el token, generar/cargar el vigente después. Probar cookie real copiada a cabecera, no solo csrf() de tests. El bearer de password change no requiere CSRF. Borrar refresh con sus mismos atributos y Path=/api/auth al hacer logout.

AuthenticationEntryPoint y AccessDeniedHandler de infraestructura serializan ProblemDetail (Jackson del proyecto), Content-Type application/problem+json, type/title/status/detail/instance. Preservar challenges Bearer/Basic que genera el mecanismo estándar, incluidos parámetros de error; no sobrescribirlos al escribir el cuerpo. CSRF usa también el handler 403 aunque el endpoint sea permitAll. Manejador web de identidad traduce errores de dominio de login/refresh/password a types específicos de la spec. No revelar email, passwords, refresh ni request/response completos.

### 6. Impacto concreto en tests existentes

Todas las rutas de la tabla son relativas a `src/test/java/com/codeja/master_acdc/`. **No desactivar filtros** ni cambiar expectativas de negocio para ocultar un 401.

| Tests | Adaptación prevista |
|---|---|
| catalog/infrastructure/adapters/in/web/ProductControllerTest.java | Importar cadena real y dependencias de test del slice; `.with(jwt())` en POST/PUT, incluidos rechazos de cuerpo y SKU; GET sigue anónimo |
| catalog/infrastructure/adapters/in/web/CreateProductIT.java | jwt() en POST; propiedades de la extensión global |
| catalog/infrastructure/adapters/in/web/ProductSearchMongoIT.java | jwt() en POST usados como preparación; GET públicos; propiedades globales |
| ordering/OrderPlacementIT.java, ordering/OrderCancellationIT.java | jwt() en todos los POST, incluidos errores y concurrencia; mantener X-Customer-Id y asserts |
| ordering/infrastructure/adapters/in/web/OrderControllerTest.java | Importar cadena en slice, jwt() en GET/POST de negocio; conservar validación y precedencias |
| ordering/infrastructure/adapters/in/web/OrderListingIT.java | jwt() en GET; propiedades globales |
| inventory/infrastructure/adapters/in/web/StockReservationIT.java | jwt() en POST; mantener contratos 201/400/404/409; propiedades globales |
| **catalog/infrastructure/adapters/in/web/CatalogProductEditingMongoIT.java** | **HUMANA**: jwt() en POST/PUT; conservar conflictos de versión |
| **catalog/infrastructure/adapters/in/web/CatalogProductCacheEditingIT.java** | **HUMANA**: jwt() en POST de preparación y PUT; GET públicos; no variar assertions de invalidación |
| **inventory/infrastructure/adapters/in/web/StockReservationConcurrencyTest.java** | **HUMANA**: HttpClient contra RANDOM_PORT requiere bearer REAL firmado con el encoder del contexto o el PEM temporal global; jwt() no actúa sobre HTTP de red. Conservar 50 peticiones, resultados 30/20 y timeout 1,5–3,5 s |

Tests que solo hacen GET público: ProductDetailControllerTest (slice: importar cadena pública real), ProductDetailMongoIT, ProductReviewQueryIT, CatalogStockProjectionSchedulingIT y **CatalogStockProjectionCacheInvalidationIT**. No requieren jwt(); los contextos completos reciben las propiedades de la extensión global sin imports adicionales. CatalogStockProjectionCacheInvalidationIT no requiere edición por la clave de test. CatalogDetailMongoRepositoryAdapterIT usa MockMvc standaloneSetup sin cadena: sigue siendo test del adaptador, no prueba de seguridad.

Otros contextos completos sin llamadas HTTP: MasterAcdcApplicationIT, InventoryFacadeIT, InventoryConcurrencyIT, InventoryReservationComparisonIT, InventoryRepositoryAdapterIT, OrderRepositoryAdapterIT, OrderSummaryQueryAdapterIT, OrderTransactionIT, OrderSummaryProfilingIT y ProductSearchQueryIT reciben las propiedades globales sin modificaciones por la clave de test. Lo mismo sucede con **StockReservationTransactionIT** y con ambos SpringApplicationBuilder de **ProductCacheMultiInstanceIT**: no necesitan imports ni cambios humanos para la clave efímera. Si un guardián falla por otro motivo, detenerse para revisión humana.

OrderSummaryQueryAdapterQueryCountTest usa DataJpaTest con import específico: no cargar seguridad ni añadir jwt(). Guardianes unitarios/ArchUnit/Mongo puros conservan sus aserciones; no agregar exclusiones a escaneo de identity. ProductDetailCacheRedisIT no justifica modificar ProductDetailCache ni TTL. La extensión global aporta solo configuración y material criptográfico de prueba, no autentica peticiones ni sustituye cadenas por otras permisivas: las tres adaptaciones HTTP de guardianes siguen siendo humanas.

### 7. Estrategia de pruebas y trazabilidad

- AuthFlowIT: PostgreSQL y Redis Testcontainers compartidos por los nuevos flujos; mantener Mongo compartido si arranca el contexto completo de catálogo. Registro/login/call, cookies reales, rotación/replay/logout/password entre dispositivos, claims, issuer/aud/exp/firma/kid, errores indistinguibles y CSRF. HTTP sobre MockMvc con cadenas reales y bearer real es suficiente; HTTPS simulado para atributos Secure.
- RefreshTokenConcurrencyIT: dos transacciones y peticiones independientes, barrera de inicio, mismo token/CSRF; exactamente 200/401 y consulta posterior de familia/sucesor revocados. No test transaccional que englobe ambas peticiones ni mocks de repositorio para locks.
- IdentityPersistenceIT: V8, índices/FK/unique, rollback de cliente con cuenta, hashes y límites de fechas; queries en PostgreSQL real. RefreshTokenLockTimeoutIT comprueba 503 y rollback.
- Tests unitarios de cada método público de caso de uso, política de contraseña, encoder de relleno, JWT/validadores y mappers. Clock fijo para 7/30 días y exp; prueba frontera de 12 caracteres y límites del encoder.
- ApiSecurityTest y ActuatorSecurityIT verifican anonimato por método, ausencia de sesión, CSRF y aislamiento Basic/Bearer, sin anticipar matriz RBAC. La prueba de categorias usa el matcher aunque no se introduzca un controller nuevo.
- Cada escenario del delta se copia exactamente a @DisplayName; nombres Java `should_<comportamiento>_when_<condición>`. Tasks referencia los grupos y tests. AuthFlowIT no se ejecuta con `mvn test`: ejecutar también verify/IT antes de aprobar implementación. PIT vigente 75%, sin bajar umbral.

## Risks / Trade-offs

- [Revocación de replay revertida por excepción] → resultado de dominio con commit antes de escribir 401, test que intenta usar el sucesor después.
- [Contención por usuario] → serialización simple que cubre refresh/logout/password/login; locks breves y timeout observable. No se añade tabla de familias ni locks distribuidos.
- [JWT robado sigue válido hasta exp] → límite explícito de este change; versión de sesión posterior. No afirmar que cambiar password invalida inmediatamente access tokens.
- [JWT CUSTOMER permite escribir catálogo y X-Customer-Id sigue manipulable] → transición solicitada solo para lab; RBAC y propiedad son el siguiente change, no anunciar seguridad final de producción.
- [Agregar Security rompe pruebas antes de configurar cadenas] → coordinar el diff local de pom.xml y las adaptaciones humanas; no dar por nuevo un fallo de baseline ni modificar el pom protegido.
- [Zonas protegidas bloquean implementación] → aprobación humana para propiedades de producción y dependencias; guardianes solo editados por una persona. No convertir esta propuesta en autorización general.
- [Lista común incompleta y coste bcrypt] → recurso versionado revisable y pruebas representativas; no prometer defensa de fuerza bruta ni rate limiting fuera de alcance.

## Migration Plan

1. Revisión de propuesta y aprobación de tareas humanas; conservar el diff local de pom.xml. Una persona incorpora en application.properties protegido el bloque exacto de la tarea 1.2: issuer https://api.master-acdc.dev sin barra final, audience master-acdc-api y enlaces de clave/OPS al entorno. Mantener exposición existente health,metrics y health.show-details=never; credenciales OPS solo por entorno y hash {bcrypt}.
2. Aplicar V8 aditiva en PostgreSQL, sin tocar customers existentes. No crear contraseñas ni cuentas automáticas para clientes históricos. Aportar PEM montado externamente, sin versionarlo.
3. Implementar y probar identity, luego activar las tres cadenas junto con adaptaciones de tests. Tests usan clave efímera compartida; desplegar todas las instancias con issuer/aud y clave coherentes.
4. Ejecutar fast, integración y comprobaciones de secretos disponibles; no introducir Gitleaks/Semgrep ni editar pipelines protegidos por cuenta propia. Registrar evidencia de PIT ≥75% cuando se ejecute, sin afirmar que fast lo mide.
5. Rollback de aplicación preserva las tablas V8 y datos; no borrar cuentas ni familias con una migración destructiva. Volver a una versión sin seguridad reabre la API: limitar acceso en despliegue y requerir decisión operativa humana, no automatizar ese rollback.
