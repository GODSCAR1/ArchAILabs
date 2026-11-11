# Design

## Context

Motivación: `proposal.md`, sección Why. Este change prepara deliberadamente la línea base vulnerable del lab S13 en `lab-s13-start`; no es el change de remediación.

Fuentes contrastadas:
- ADR 0009 y `docs/catalog-model.md` describen la transición documental; su texto de coexistencia es histórico. El código actual usa `ProductSearchMongoRepositoryAdapter` y `V7__drop_postgres_catalog.sql` elimina el catálogo relacional. El usuario ha confirmado sembrar SQLi en pedidos.
- `docs/decisiones/listado-de-pedidos.md` fija una proyección de coste constante y conservación de clientes huérfanos. `OrderSummaryQueryAdapter.findByStatus` usa hoy Criteria/Specification, `left join` y un mapper dedicado.
- ADR 0011 y specs `authentication`, `authorization`, `api-security` mantienen API stateless, recuperación de acceso sin cookies y revocación de sesión antes del commit. No hay recuperación ni descarga de exports actualmente.
- `HttpSecurityConfiguration.apiSecurity` abre solo los POST existentes y restringe `/api/admin/**` a ADMIN. `IdentityExceptionHandler` limita su advice a controladores enumerados.
- El guardián `OrderSummaryQueryAdapterQueryCountTest` construye `OrderListingCriteria(status, scope)`. La persona revirtió la preparación anticipada de H3; queda pendiente para inmediatamente antes de `/opsx-apply`, conservando 120 filas y umbral ≤ 2. Durante esta revisión de planificación se mantiene el constructor original.

## Goals / Non-Goals

**Goals:**
- Dejar exactamente tres puntos vulnerables: CWE-89, CWE-338 y CWE-22, con trazabilidad para el triaje posterior.
- Conservar las fronteras hexagonales y los contratos existentes ajenos a esas siembras.
- Preparar casos de uso ejecutables y tests nuevos del agente solo de camino feliz; la matriz protegida la amplía una persona.

**Non-Goals:**
- Neutralizar las siembras, probar su explotación ahora o garantizar qué rule id de un escáner las detectará.
- Introducir secretos versionados, tokens en logs/respuestas, correo real o un endpoint para consultar el buzón.
- Cambiar JWT, `Role`, CSRF de refresh/logout, caches, catálogo o dependencias.

## Decisions

### 1. SQL nativo con ORDER BY vulnerable en el adaptador existente

Recorrido: `OrderController.list(status, sort)` → `OrderWebMapper.listingCriteria(status, sort)` → `OrderListingCriteria(status, scope, sort)` → `AuthorizedListOrdersByStatusUseCase.list` → servicio → `OrderSummaryQueryAdapter.findByStatus`.

- `sort` es una cadena opcional; ausencia usa `id ASC`. Si está presente se conserva sin normalizar, parsear ni validar contra lista blanca. El decorador reconstruye el criterio con `actor.readingScope()` y conserva `sort`, sin usarlo para determinar identidad.
- El adaptador mantiene su constructor con `EntityManager` y ejecuta una query nativa, observable por las estadísticas Hibernate. No se pasa a JdbcTemplate para que el contador existente siga midiendo la consulta real.
- La consulta conserva proyección de cuatro columnas, `left join customers`, `left join order_lines`, `count(l.product_id)`, `coalesce(sum(l.quantity), 0)` y agrupación; no carga entidades ni hace consultas por pedido.
- `status` y el cliente del alcance Own siguen como parámetros enlazados; Global no añade filtro de cliente y None devuelve vacío. No se concatena ningún otro dato de petición.
- Alias SQL explícitos sin comillas `id`, `customerName`, `lineCount`, `totalUnits` permiten el ejemplo `sort=totalUnits DESC` (PostgreSQL pliega alias y referencia a minúsculas). La cláusula se construye concatenando directamente `" ORDER BY " + criteria.sort()`; para un orden explícito se añade `, id ASC` como desempate, sin sanear el fragmento recibido.
- `OrderSummaryPersistenceMapper` concentra la conversión de resultados nativos a `OrderSummaryRow` y dominio, incluyendo UUID, números agregados y nombre nullable; ni el adaptador ni el DTO hacen el mapeo.

**Excepción temporal confirmada:** para este único listado se suspenden consultas tipadas mediante Specification/metamodelo, prohibición de concatenar SQL y lista blanca de ordenación. El change de remediación del lab S13 elimina esta excepción y corrige la causa con tests rojo→verde. Esta autorización no se extiende a otros adaptadores, filtros ni endpoints. El reemplazo de JPQL/Criteria por proyección nativa conserva la decisión de coste constante del ADR de pedidos.

Alternativas descartadas: reintroducir PostgreSQL en catálogo contradice V7 y la arquitectura actual; una lista blanca o un parser de sort impedirían la siembra solicitada.

**Migración de firma:** añadir el tercer componente al record y actualizar todos sus callers reales, incluidos mapper, decorador, tests de dominio/aplicación/web, `MethodAuthorizationTest`, IT y profiling. No añadir constructor de dos argumentos ni sobrecarga para mantener en verde el guardián. La adaptación protegida H3 la hace una persona inmediatamente antes de `/opsx-apply`, no durante esta actualización de planificación; cualquier otro choque con un guardián detiene apply.

### 2. Recuperación en identity, con aleatoriedad débil separada de refresh

API propuesta:
- POST `/api/auth/password-reset/request` con `{email}` → 202 sin cuerpo.
- POST `/api/auth/password-reset/confirm` con `{email,token,newPassword}` → 204 sin cuerpo.
- Ambos sin bearer/cookies; normalización y validación de email consistentes con identity. Email válido desconocido o cuenta deshabilitada recibe el mismo 202 sin notificación. El token solo viaja en el body de confirmación y en el puerto privado de notificación, nunca en URL, logs o respuestas.

Nuevos elementos bajo `src/main/java/com/codeja/master_acdc/identity/`:
- `application/port/RequestPasswordResetUseCase` y `ConfirmPasswordResetUseCase`; implementaciones `application/RequestPasswordResetService` y `ConfirmPasswordResetService`.
- Records/comandos en `domain/model/`: solicitud, confirmación y token persistible con hash y vencimiento. Validan presencia y formas básicas sin analizar la calidad aleatoria del token.
- Interfaces de salida en `domain/port/`: `PasswordResetSecrets` (generar y calcular hash), `PasswordResetTokens` (guardar/consultar/consumir por cuenta) y `PasswordResetNotifier` (enviar destinatario y token).
- `infrastructure/adapters/out/security/WeakRandomPasswordResetSecretGenerator.generate`: el bean singleton mantiene una única instancia de `java.util.Random` en un campo `private final Random random = new Random();`, creada una sola vez al construir el bean y compartida entre todas las generaciones y cuentas. `generate` usa ese campo para producir 32 bytes con `nextBytes`, en representación hexadecimal; nunca crea `new Random()` por llamada, reinicializa el generador ni usa un generador por cuenta. Ninguna mezcla con SecureRandom ni generador alternativo. SHA-256 para almacenamiento: conservar hash, expiración y consumo no corrige la predictibilidad del PRNG.
- `infrastructure/adapters/out/notification/InMemoryPasswordResetNotifier.notify`: un único último mensaje inmutable en referencia atómica, con destinatario normalizado y token. Acceso de test `lastMessageFor(email)` solo si coincide el destinatario del último mensaje; no historial, endpoint, logging ni `toString` que imprima secretos. Es el adaptador de laboratorio ensamblado, no correo real.
- `infrastructure/adapters/out/persistence/PasswordResetTokenJpaEntity`, repositorio, adaptador y `PasswordResetTokenPersistenceMapper`; `V9__create_password_reset_tokens.sql` crea una fila por cuenta (`user_id` PK/FK, `token_hash`, `expires_at`). Una nueva solicitud reemplaza el token anterior y una confirmación lo elimina. No se edita V8.
- `infrastructure/adapters/in/web/PasswordResetController`, DTO de solicitud/confirmación y `PasswordResetWebMapper`; ampliar la lista de controladores del advice `IdentityExceptionHandler`.
- `infrastructure/config/PasswordResetConfiguration` y envolturas transaccionales de los casos de uso. Beans sin lógica de negocio.

La solicitud identifica la cuenta y toma su bloqueo antes de reemplazar su fila de recuperación. Notifica tras confirmar la persistencia; se reutiliza el soporte de transacciones de identity. La confirmación busca por email, toma `UserAccounts.findForUpdate`, comprueba cuenta habilitada y hash/vigencia del token bajo ese bloqueo, valida `PasswordPolicy`, cambia hash, incrementa `SessionVersion`, revoca familias, consume el token y publica `SessionVersionBarrier` antes del commit. Se mantiene orden cuenta→credenciales. Las operaciones que cambian el token de una cuenta se serializan por ese mismo bloqueo, sin necesidad de un lock independiente invertido.

La confirmación usa `SessionRevocationTransactions` como `TransactionalPasswordChange`: fallos de barrera o commit conservan el contrato actual. Un intento inválido no altera contraseña, versión, familias, pedidos ni stock. Token incorrecto, vencido, consumido o de otra cuenta → 400 `https://api.master-acdc.dev/problems/invalid-password-reset-token`; contraseña inválida → 400 `.../invalid-password`; cuerpo/email inválido → 400 `.../invalid-password-reset-request`; contención → 503 `.../authentication-busy`; barrera/commit indisponible → 503 `.../session-version-unavailable`, ambos con Retry-After 1. Mensajes de error constantes, sin datos de entrada. No se añaden tests negativos en este change del agente.

El estado LCG compartido es parte deliberada de S13-02: en una secuencia controlada de generaciones, observar el token de una cuenta propia permite reconstruir el estado y predecir el siguiente token de otra cuenta. Esa será la base del test rojo del change de remediación; no se implementa ni se declara verificado en este change.

Alternativas descartadas: crear `new Random()` en cada llamada rompe la continuidad de estado que necesita esa demostración; reutilizar y debilitar `SecureRefreshSecretGenerator` introduciría otra vulnerabilidad en refresh; retornar o loguear el token introduciría una exposición adicional; SMTP requiere infraestructura no solicitada. Se fija vida de 15 minutos y una fila por cuenta como detalles mínimos del flujo, sin mitigaciones del PRNG débil.

### 3. Exports como módulo pequeño independiente

Bajo `src/main/java/com/codeja/master_acdc/exports/`:
- `application/port/DownloadExportUseCase` y `application/DownloadExportService` para delegar la lectura.
- `domain/port/ExportFiles`, modelo inmutable del contenido (copias defensivas de bytes) y error de dominio sin ruta interna.
- `infrastructure/adapters/in/web/AdminExportController` para GET `/api/admin/exports?name={name}`, con `@GetMapping("/api/admin/exports")` y query param obligatorio `@RequestParam("name")`. `ExportWebMapper` final/estático y decorador no final `AuthorizedDownloadExportUseCase` con `@PreAuthorize("hasRole('ADMIN')")`.
- Recorrido: query param `name` → controlador/mapper → caso de uso/decorador → `infrastructure/adapters/out/filesystem/LocalExportFiles.read(name)`, que construye `base.resolve(name)` y lee el archivo con `Files.readAllBytes`. No usa `normalize`, `toRealPath`, `startsWith(base)`, limpieza de separadores, lista blanca ni rechazo de `..`.
- `infrastructure/config/ExportConfiguration` ensambla carpeta `Path.of("exports")`, adaptador, servicio y decorador; tests inyectan un directorio temporal. No se requiere editar application properties ni crear archivos de exports en la propuesta.
- Respuesta 200 `application/octet-stream` con bytes; no se refleja el nombre del usuario en cabeceras. Archivo inexistente → 404 `https://api.master-acdc.dev/problems/export-not-found`; otros errores concretos de E/S → 500 `.../export-read-failed` sin ruta interna. Pedidos/stock/archivo permanecen sin cambios.

La ruta administrativa aprovecha la restricción existente de la cadena HTTP y la comprueba también en el decorador; no necesita permiso nuevo ni edición de `Role`. Se mueve el nombre al query param porque `StrictHttpFirewall` rechaza separadores codificados como `%2F`, `%5C` y segmentos `../` en el path con 400 antes de llegar al controlador. Esto también afecta a MockMvc al pasar por `FilterChainProxy`: una prueba que solo esperase el rechazo podría pasar antes de remediar el adaptador y no demostraría el cambio rojo→verde. En el query param, el nombre llega a `base.resolve(name)` sin modificar el firewall ni la decodificación de URL. La demostración con payloads adecuados al sistema de archivos se reserva al siguiente change; no se afirma aquí que se haya ejecutado.

Alternativas descartadas: transportar el nombre como variable de path bajo `/api/admin/**` impide demostrar el fallo si `StrictHttpFirewall` rechaza antes la petición; GET `/api/exports?name={name}` obligaría a otra excepción de autorización. Se elige GET `/api/admin/exports?name={name}` manteniendo ADMIN. `base.resolve(name).normalize()` con comprobación de confinamiento sería la remediación y queda fuera de esta siembra.

### 4. Intervenciones humanas y dependencias de apply

Antes de implementar, una persona completa las tareas H1–H3 de `tasks.md`:
1. Añadir solo las dos rutas POST exactas al bloque público actual de `HttpSecurityConfiguration.apiSecurity`, sin wildcard y sin modificar `cookieAuthenticationRequests`.
2. Ampliar `SecurityMatrixTest`/`SecurityMatrixSupport` con las tres filas de `specs/authorization/spec.md`, incluida GET `/api/admin/exports?name=pedidos.csv`, fixtures de cuenta/token independiente del actor y un archivo temporal. Mantener intactas las filas previas; el alcance propio/ajeno del pedido no cambia. Confirmación se prepara con un token del destinatario distinto del actor para que su revocación no contamine otras celdas.
3. Inmediatamente antes de `/opsx-apply`, adaptar el constructor de `OrderListingCriteria` en `OrderSummaryQueryAdapterQueryCountTest` añadiendo `"id ASC"`, conservando toda la medición. H3 permanece pendiente tras la reversión humana. La firma nueva y esa llamada se integran coordinadamente durante apply; no se pide al agente inventar compatibilidad.

Estas intervenciones pueden quedar rojas hasta que existan los endpoints/firma; son preparación humana de un cambio coordinado, no permiso para debilitar guardianes. En particular, H3 no se adelanta a la fase de planificación: se ejecuta inmediatamente antes de apply y la implementación integra entonces el nuevo componente del record. Al iniciar apply se revisan las ediciones y su confirmación humana antes de modificar código. Si falta alguna, se informa del bloqueo y se detiene implementación.

### 5. Reglas de arquitectura

Dependencias que debe comprobar ArchUnit (no se afirman verificadas en esta propuesta):
- `identity.application` → su propio `domain` y contratos de aplicación; `ordering.application` → su propio `domain`; `exports.application` → su propio `domain`. Ninguna aplicación importa infraestructura o Spring/JPA/Jackson.
- `identity` no depende del negocio de `exports`, inventory ni ordering para recuperar contraseñas; se mantienen solo las integraciones previas de identidad con las API públicas existentes.
- `exports` no depende de catálogo, identity, ordering ni inventory: Spring Security proporciona authorities en infraestructura.
- `ordering` conserva integración de inventory por su API pública; inventory nunca depende de ordering. Ningún nuevo acceso a cache o Mongo desde pedidos.
- Puertos de salida solo interfaces en `domain/port`; mappers final/sin estado/estáticos solo en infraestructura. DTO y entidades no convierten; controladores y adaptadores delegan el mapeo.
- Transacciones solo en `infrastructure/config`; decoradores autorizados no final y proxificables. Leer `package-info.java` antes de añadir clases y documentar los paquetes nuevos del módulo exports.

El agente puede añadir un test específico `ExportsArchitectureTest` de dependencias del módulo nuevo, sin editar guardianes protegidos; se conservan los tests existentes de fronteras y mappers.

### 6. Registro de triaje de las siembras intencionadas

Rutas relativas a `src/main/java/com/codeja/master_acdc/`. Las líneas quedan pendientes hasta implementar y ejecutar Spotless; no son ubicaciones ya existentes ni hallazgos verificados.

| ID | Categoría | Fichero previsto | Método previsto | Punto vulnerable | Línea final |
|---|---|---|---|---|---|
| S13-01 | CWE-89: SQL injection | `ordering/infrastructure/adapters/out/persistence/OrderSummaryQueryAdapter.java` | `findByStatus` (o helper de construcción nativa llamado desde él) | petición sort → concatenación directa en ORDER BY → consulta nativa PostgreSQL | 32 |
| S13-02 | CWE-338: aleatoriedad débil | `identity/infrastructure/adapters/out/security/WeakRandomPasswordResetSecretGenerator.java` | campo final `random` y `generate` | una única instancia de java.util.Random creada al construir el bean singleton, compartida entre todas las generaciones; nextBytes → token; estado LCG reconstruible desde un token propio para predecir el siguiente de otra cuenta; nunca new Random() por llamada | 12, 17 |
| S13-03 | CWE-22: path traversal | `exports/infrastructure/adapters/out/filesystem/LocalExportFiles.java` | `read` | query param name de GET /api/admin/exports?name={name} → base.resolve(name) → lectura sin confinamiento | 23 |

Son intencionadas para el lab S13. No añadir validaciones ni sustituir el sink que las neutralicen. Una nota local breve en cada sink explica la siembra; no usa supresiones del escáner. El change posterior escribirá los tests de seguridad rojo→verde y eliminará las tres causas y la excepción SQL.

## Risks / Trade-offs

- [La rama contendrá tres vulnerabilidades reales de diseño] → Alcance explícito confirmado por el usuario; registrar sinks y líneas al cerrar la implementación para el triaje/remediación posterior, sin mitigarlas aquí.
- [La consulta nativa puede cambiar tipos o conteos] → Test feliz PostgreSQL real, métricas correctas con joins, cliente huérfano y cero líneas; conservar guardianes y contador Hibernate real.
- [La revocación de sesión podría omitirse en el flujo nuevo] → Reutilizar puertos, bloqueos y envoltura transaccional existentes; test feliz comprueba nuevo hash/versión/familias/barrera sin crear tests de ataques.
- [El notificador conserva solo el último mensaje y se pierde al reiniciar] → Límite deliberado del adaptador de laboratorio; tests secuenciales/aislados obtienen el mensaje antes de otra solicitud. Correo durable requeriría otro change.
- [Una detección SAST no demuestra explotación HTTP del traversal] → Usar query param para que el nombre alcance el adaptador sin el rechazo previo del path por StrictHttpFirewall; conservar el firewall y dejar los tests de explotación al change siguiente, sin inventar resultados.
- [Un guardián o regla adicional impide la siembra] → Detener apply y consultar a una persona; no suprimir reglas, editar configuración protegida ni rebajar PIT (75%).

## Migration Plan

1. Revisar estos artefactos; preparar H1/H2 en `lab-s13-start` y ejecutar H3 inmediatamente antes de `/opsx-apply`, manteniéndola pendiente hasta ese momento.
2. Mediante un nuevo pedido explícito de apply, implementar los tres recorridos y sus tests felices. Crear solo V9; Flyway mantiene las migraciones anteriores intactas.
3. Aplicar Spotless, completar las líneas del triaje, actualizar el grafo tras modificar código y ejecutar los controles requeridos sin bajar umbrales. No ejecutar auditoría/remediación como parte de esta propuesta.
4. El usuario inicia la auditoría sobre esa línea base y el siguiente change incorpora las pruebas de explotación y correcciones. No archivar ni sincronizar specs automáticamente.
5. Rollback de la implementación: revertir código y ediciones humanas de acceso/matriz de forma coordinada; el esquema aditivo de recuperación puede quedar sin consumidores hasta una migración posterior revisada. No borrar cuentas, pedidos, stock ni alterar versiones ya publicadas.
