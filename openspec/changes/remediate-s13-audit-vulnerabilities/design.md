# Design

## Context

Motivación: `proposal.md`, Why. Se ha contrastado el código en `lab/s13`, el diseño de siembra (sección 6), `docs/lab-s13.md` (1.2), el ADR de listado de pedidos y ADR 0011.

- `OrderSummaryQueryAdapter.findByStatus` concatena `criteria.sort()` en SQL nativo. `OrderSpecifications` ya ofrece filtros tipados de estado y alcance. `OrderListingCriteria(status, scope, sort)` es un record de tres componentes; se conserva su firma, el constructor del adaptador y los callers protegidos.
- `WeakRandomPasswordResetSecretGenerator` usa un `Random` compartido, 32 bytes con `nextBytes` y hexadecimal. Lo ensamblan `PasswordResetConfiguration` y su test unitario; el puerto sigue siendo `PasswordResetSecrets`.
- `LocalExportFiles` lee `base.resolve(name)` directamente. `ExportExceptionHandler` solo distingue 404 y 500. `AdminExportDownloadIT` ya permite inyectar una carpeta temporal; el query param atraviesa el firewall sin cambiarlo.
- Hay pruebas felices `OrderListingSortIT`, `PasswordResetFlowIT`, `AdminExportDownloadIT`. Las queries se verifican contra PostgreSQL real; los tests HTTP reutilizan soporte de identidad y contenedores.
- No existe aún `semgrep-rules/`. `.semgrepignore` conserva exclusiones por defecto y archivos históricos; no excluye producción.
- La siembra está completa pero no archivada: `password-recovery`, `admin-exports` y la ordenación de pedidos solo tienen deltas activos. Los nuevos requisitos son adiciones de restricciones con nombres distintos; la revisión humana debe integrar siembra antes de remediación, sin que el agente sincronice nada. El contrato amplio de sort de la siembra queda restringido por la lista blanca de este change.

## Goals / Non-Goals

**Goals:**
- El mismo test de regresión ejecuta rojo y verde sin alteración de su aserción de seguridad.
- Corregir los sinks y conservar contratos de dominio, alcance de datos y coste de consulta.
- Cubrir los patrones reales mediante reglas compatibles con Semgrep CE 1.182.0, sin asumir análisis interarchivo.

**Non-Goals:**
- Crear una infraestructura genérica de parsing, notificaciones o análisis estático; cambiar dependencias o firmas guardianas.
- Declarar una prueba de impredecibilidad criptográfica a partir de muestras: el test es regresión del ataque LCG concreto.
- Ampliar el modelo de confianza de exports a enlaces simbólicos o escrituras concurrentes: se aplica el confinamiento léxico normalizado solicitado.

## Decisions

### 1. Protocolo obligatorio de ejecución por vulnerabilidad

Orden total: S13-01 rojo → commit humano → corrección → regla → S13-02 rojo → commit humano → corrección → regla → S13-03 rojo → commit humano → corrección → regla. No adelantar tests rojos ni arreglos de otra vulnerabilidad.

Cada test rojo se escribe y ejecuta en un turno exclusivo de rojo. Mostrar comando, fallo por la aserción de seguridad y salida completa de Verify. DETENERSE antes de corregir, escribir la regla o abordar otra vulnerabilidad. El usuario hace el commit; el agente no hace commits. Solo una petición posterior de continuación tras confirmar el commit habilita el verde. Fallos de compilación, Docker, conexión o fixtures no son evidencia roja válida: corregir únicamente el montaje del test hasta demostrar el fallo de seguridad.

Ejecutar `bash ./mvnw spotless:apply` y `bash ./verify-quality.sh --fast` también al cerrar el turno rojo. El fallo debido a la regresión intencional se conserva y explica; no desactivar ni debilitar el test. Si un IT no pertenece al fast loop, su ejecución dirigida sigue siendo obligatoria y Verify podría pasar: no inventar un fallo del harness. Al cerrar un verde, ejecutar test dirigido y Verify en verde antes del siguiente rojo.

### 2. S13-01 — ordenación de pedidos

**Hallazgo:** CWE-89, `OrderSummaryQueryAdapter.java:32`: entrada `sort` concatenada en ORDER BY antes de `createNativeQuery`.

**Impacto:** un lector autorizado puede alterar la consulta y potencialmente acceder o afectar datos con los permisos del usuario SQL. No se afirma que PostgreSQL ejecute múltiples sentencias para todo payload: un error SQL/500 frente al 400 requerido también revela la ausencia de validación, pero no prueba por sí solo ejecución de DROP.

**Test:** ampliar `OrderListingSortIT` o añadir una clase HTTP de regresión equivalente, con PostgreSQL Testcontainers y bearer SUPPORT. Payload `totalUnits; drop table orders--`, 400, `application/problem+json`, type `invalid-order-sort`, pedidos intactos, stock intacto y un GET válido posterior 200. Comprobar persistencia en una transacción independiente de la petición para que una transacción SQL abortada no oculte el estado. Asegurar que las verificaciones de conservación se ejecutan aunque falle la expectativa HTTP (p. ej. aserciones agrupadas). Conservar la prueba feliz `totalUnits DESC` y añadir empates, lista blanca, direcciones inválidas y ausencia de sort. Escenarios exactos en `specs/ordering/spec.md`.

**Corrección:** validar la gramática mínima `campo [ASC|DESC]`, sensible a mayúsculas como los identificadores del contrato, con espacios exteriores normalizados. Sin sort usa `id ASC`; campo sin dirección usa ASC; vacío, comas, tokens extra y expresiones se rechazan. Centralizar la validación en dominio, invocada desde el criterio existente, sin cambiar sus tres componentes. El mapper/advice traduce el error concreto a 400 `https://api.master-acdc.dev/problems/invalid-order-sort`; nunca refleja el payload.

Restaurar Criteria con proyección `OrderSummaryRow`, `Specification.allOf(hasStatus, visibleTo)` y metamodelo; mapear cada campo permitido a una expresión tipada (id, nombre del cliente, count de productId o suma de quantity). Aplicar asc/desc tipados y añadir id ASC al final, sin duplicarlo si ya se ordena por id. No construir ORDER BY textual, ni siquiera con fragmentos de lista blanca: esto retira también la excepción de SQL concatenado, no solo la inyección. Mantener left joins, clientes huérfanos, cero líneas con count de columna no nula, coalesce de unidades y scope None vacío. Reutilizar mapper `toDomain`; retirar conversión nativa solo tras comprobar sus callers y ajustar sus tests no protegidos.

**Regla:** `shop-order-sort-injection`, patrón local que reconoce concatenación de `criteria.sort()` en consulta/ORDER BY (incluida variable SQL intermedia usada por el sink). Fixture reproduce exactamente la siembra y una consulta tipada segura. La CE no necesita seguir controller → puerto. Alternativa descartada: parametrizar un identificador ORDER BY no define el orden deseado; sanear caracteres aislados no sustituye lista blanca.

**Retirada de excepción:** desde la corrección de S13-01 deja de tener vigencia la autorización temporal del diseño de siembra §1. Retirar comentarios de siembra del código y documentar en `docs/lab-s13.md` §4 que los tres mandatos vuelven a aplicarse: filtros tipados, lista blanca y ausencia de SQL concatenado. El diseño histórico se conserva como evidencia, no como permiso vigente. Guardianes de 120 filas/≤2 consultas y alcance permanecen intactos.

### 3. S13-02 — tokens de recuperación

**Hallazgo:** CWE-338, generador compartido `WeakRandomPasswordResetSecretGenerator`, campo `random` y `generate` (líneas 12/17).

**Impacto:** quien recibe un token propio puede reconstruir el LCG de 48 bits y predecir el siguiente token destinado a otra cuenta en una secuencia controlada. Hash, expiración y consumo no aportan entropía.

**Test:** añadir `PasswordResetPredictionTest` de regresión sobre el puerto/generador compartido y una prueba de dos cuentas en `PasswordResetFlowIT` con solicitudes consecutivas 202. Leer el token propio del notificador antes de solicitar el ajeno porque solo conserva el último mensaje; no hay endpoint de lectura ni logs. El oráculo de predicción vive solo en tests: decodificar los 32 bytes hex, reconstruir las ocho salidas `nextInt` respetando el orden little-endian usado por `Random.nextBytes`, enumerar los 2^16 bits inferiores del estado tras la primera salida y filtrar con las otras siete. Avanzar el estado y reconstruir los siguientes 32 bytes.

La aserción de seguridad es que ninguna predicción compatible con el token observado coincide con el siguiente token real. Si no existe estado compatible (lo esperable con SecureRandom), el ataque no produjo una predicción y el test pasa, sin exigir falsamente que el LCG siga siendo reconstruible. Autoverificar el oráculo con una secuencia `Random` de semilla conocida, confinada al test y sin secretos reales. No usar reflexión, semilla del generador real ni igualdad de dos tokens consecutivos como sustituto del ataque. La prueba unitaria mantiene el rojo en el fast loop; la HTTP demuestra cuentas distintas. `@DisplayName("Token propio no permite predecir el siguiente token ajeno")` traza el escenario.

**Corrección:** renombrar a `SecurePasswordResetSecretGenerator`; sustituir el campo por `SecureRandom`, conservando 32 bytes, hexadecimal y SHA-256. Seguir el patrón de `SecureRefreshSecretGenerator` sin cambiar este último ni la codificación pública de recuperación. Actualizar ensamblaje, imports, test unitario y referencias de código al nombre anterior. El test rojo usa el puerto o ensamblaje para que el renombrado no elimine su protección. Mantener los tests felices de expiración/consumo y la revocación ya existente.

**Regla:** `shop-no-weak-password-reset-random`, patrón de `new java.util.Random()`/import equivalente como fuente de secretos de recuperación, incluyendo instancia en campo y `nextBytes`. Fixture reproduce el singleton real y acepta SecureRandom. Acotar al contexto de generación de secretos, no prohibir simulaciones aleatorias de tests. Alternativa descartada: crear un Random por cuenta/llamada solo cambia la explotación y sigue sin ser criptográfico.

### 4. S13-03 — lectura de exports

**Hallazgo:** CWE-22, `LocalExportFiles.read`, línea 23: `Files.readAllBytes(base.resolve(name))` sin confinamiento.

**Impacto:** ADMIN puede leer archivos ajenos a exports accesibles al proceso, sin que sus permisos administrativos autoricen lectura arbitraria del servidor.

**Test:** ampliar `AdminExportDownloadIT` con un fichero externo bajo una raíz temporal controlada y contenido centinela, bearer ADMIN y query param `name`. Casos: `../<fichero>`, recorrido con separadores nativos y ruta absoluta externa. En Windows cubrir `..\<fichero>`; en POSIX usar el separador efectivo, sin tests desactivados ni esperar que una barra inversa sea separador POSIX. Cada caso exige 400, type `invalid-export-name`, cuerpo sin centinela ni ruta interna y conservación de archivos/pedidos/stock. La carpeta hermana con prefijo común (`exports-otro`) cubre que `Path.startsWith` no es `String.startsWith`. La descarga interna existente continúa en 200. Los casos deben llegar al adaptador, no pasar por rechazo anticipado del firewall.

**Corrección:** en constructor guardar `base.toAbsolutePath().normalize()`. Antes de leer, `candidate = base.resolve(name).normalize()` y exigir `candidate.startsWith(base)` con objetos Path. Si no cumple, error de dominio `invalid-export-name`, traducido a 400 por `ExportExceptionHandler`; InvalidPathException también se traduce a 400, mientras NoSuchFileException conserva 404 y IOException real conserva 500. No comparar strings ni confiar en que normalize sin startsWith baste. No devolver bytes al fallar. Las rutas absolutas externas se rechazan por la misma comprobación, sin decisión adicional sobre rutas internas equivalentes.

**Regla:** `shop-export-path-traversal`, patrón del sink directo `Files.readAllBytes($BASE.resolve($NAME))` usado por la siembra, con negativos de la secuencia normalize + startsWith + lectura del candidato validado. Es una regla del patrón conocido, no una prueba universal de confinamiento para cualquier implementación. Alternativa descartada: rechazar solo la subcadena `../` falla en rutas absolutas y separadores del host.

### 5. Reglas adicionales y aislamiento de fixtures

Siete rule ids en total, cada uno con YAML y Java homónimos bajo `semgrep-rules/`, comentarios `// ruleid: <id>` y `// ok: <id>` colocados en la línea correcta. Las tres reglas anteriores se crean después del verde correspondiente.

| Rule id adicional | Cobertura mínima y negativos |
|---|---|
| `shop-sql-concatenation` | Construcción concatenada usada en consultas SQL/JPQL, directa o mediante variable local; aceptar consultas parametrizadas y operaciones Criteria. No marcar concatenaciones no SQL. |
| `shop-sql-injection-taint` | Taint intraprocedural desde entrada de petición o `criteria.sort()` al sink SQL, incluyendo asignaciones intermedias. Fixture positivo local y negativo parametrizado; no afirmar que CE cruza puertos o archivos. |
| `shop-no-customer-id-param` | `customerId` como selector público en query/path/header/body, incluyendo nombres explícitos de anotación y campos/componentes de DTO de entrada. Aceptar identificadores internos de dominio/persistencia y cliente resuelto desde la cuenta. Acotar producción a adaptadores de entrada web; no esconder matches reales. |
| `shop-no-log-tokens` | Llamadas logger SLF4J (niveles trace/debug/info/warn/error) que reciben, interpolan o concatenan variables de nombre exacto token/accessToken/refreshToken/password. Aceptar mensajes constantes y métricas no sensibles; restringir al logger para no confundir otros métodos info. |

Las fixtures positivas de SQL incluyen la forma nativa sembrada, no solo JDBC directo. La taint puede solaparse con patrones; no es un error ni evidencia de análisis interarchivo. No añadir supresiones al código corregido.

Ejecutar `semgrep --test semgrep-rules/` antes de añadir a `.semgrepignore` la carpeta con motivo: contiene vulnerabilidades sintéticas que no pertenecen a la auditoría del proyecto. Volver a ejecutar exactamente ese comando después y comprobar que sigue descubriendo y comprobando los siete ids. No aceptar un éxito con cero tests ni utilizar la exclusión para ocultar producción. Si CE requiere ajustar cómo se descubren los fixtures, resolverlo en sus ubicaciones/convenciones conservando el comando solicitado y la auditoría ordinaria.

Auditoría final: `semgrep scan --config semgrep-rules/ --metrics=off --error src/main/java`, más los perfiles `p/java` y `p/owasp-top-ten` de la línea base sobre el proyecto. Guardar informes bajo target y confirmar que no aparecen fixtures en targets/hallazgos; `--test` verde por sí solo no valida el escaneo real. Ningún hallazgo adicional se acepta ni se corrige fuera de alcance sin consultar.

### 6. Reglas de arquitectura

Dependencias que comprobará ArchUnit, sin atribuir comprobaciones nuevas a reglas aún pendientes:
- `ordering.application`, `identity.application`, `exports.application` solo dependen de su dominio/contratos permitidos; nunca infraestructura, Spring, JPA ni Jackson.
- Puertos en domain; adaptadores y mappers estáticos final en infraestructura; errores y criterios de dominio sin dependencias web.
- `ordering` usa inventory mediante su API pública; inventory nunca depende de ordering. No se introduce acceso a catálogo/cache para decidir stock.
- `identity` no depende de exports/ordering/inventory para generar o recuperar secretos; `exports` no depende del negocio de otros módulos.
- Ensamblaje/transacciones solo en infrastructure/config, sin lógica de negocio en beans. Decoradores de autorización siguen proxificables y las consultas conservan el alcance de la cuenta.
- Leer package-info antes de añadir clases. Si una firma guardiana o zona protegida exige cambio, detener apply y consultar; no crear compatibilidad para sortearlo.

## Risks / Trade-offs

- [Predicción LCG mal implementada o generaciones intercaladas] → Oráculo autoverificado, secuencia aislada y lectura del notificador antes de sobrescribir; nunca convertir errores de montaje en evidencia roja.
- [Reglas locales más estrechas que toda la clase de vulnerabilidad] → Documentar alcance/limitaciones en §5 del lab y demostrar con la forma exacta sembrada y negativos corregidos.
- [Confinamiento léxico no impide escapes mediante symlinks] → Base administrada sin enlaces creados por usuarios; este change cubre traversal léxico solicitado. Si el modelo real permite enlaces no confiables, detener y consultar antes de ampliar solución/contrato.
- [Consulta tipada cambia agregados o cliente huérfano] → Ejecutar IT existentes y guardián de coste constante sin tocar su firma ni sus umbrales.
- [Sort anteriormente arbitrario pierde compatibilidad] → Cambio deliberado de seguridad, marcado BREAKING; documentar gramática permitida.
- [Guardianes o reglas protegidas chocan con una corrección] → Pedir decisión humana antes de editar, sin supresiones ni rebajar PIT 75%.

## Migration Plan

1. Revisar propuesta y deltas. Una petición explícita `/opsx-apply` inicia solo el primer turno rojo.
2. Seguir las tres parejas rojo/verde con sus paradas humanas; actualizar grafo tras cambios de código en cada turno.
3. En cada verde registrar evidencia real (comando, resultado y commit rojo indicado por el usuario); trasladar hallazgo/impacto/test/corrección/regla a `docs/lab-s13.md` §4. Tras reglas adicionales completar §5 con ids, ejecución y limitaciones CE.
4. Cerrar con pruebas dirigidas, suite de reglas, auditoría y Verify, sin modificar harness ni umbrales. Sin cambios de esquema ni migración de tokens: formato, hash y vigencia se mantienen.
5. Rollback coordinado de una corrección reabre su hallazgo; no reactivar la excepción temporal ni desplegar el código vulnerable como solución. Conservar tests y evidencia, resolver fallos hacia delante. Archivado/sincronización quedan a revisión humana.
