# AGENTS.md

Las reglas marcadas "(pendiente: … Sxx)" aún NO tienen verificador automático: cúmplelas igual y no las describas como verificadas.

## Comandos (tu shell en Windows es PowerShell: invoca los scripts con `bash`)
- Compilar y probar: `bash ./mvnw -q test`. Formato: `bash ./mvnw spotless:apply`.
- Verify: `bash ./verify-quality.sh --fast` (specs + Spotless + Checkstyle + tests). Solo estilo: `bash ./mvnw -q checkstyle:check`.

## Arquitectura (hexagonal estricta)
- `domain/` NUNCA importa Spring, JPA ni Jackson (`@Entity`, `@Repository`, `@Service`, `@Component` prohibidos ahí). (pendiente: ArchUnit en S17)
- `application/` depende solo de `domain/`; NUNCA de `infrastructure/`. (pendiente: ArchUnit en S17)
- Puertos de salida en `domain/port/` (solo interfaces); implementaciones en `infrastructure/adapters/`.
- Casos de uso: interfaz en `application/port/` (`<Accion>UseCase`) + implementación `<Accion>Service` en `application/`. Adaptadores en `infrastructure/adapters/in/<tecnología>` y `infrastructure/adapters/out/<tecnología>`.
- Los beans se ensamblan en `infrastructure/config/` con `@Bean`. Sin lógica de negocio en métodos `@Bean`.
- NUNCA `ApplicationContext.getBean()` ni campos estáticos para acceder a beans fuera de `infrastructure/config/`. (pendiente: ArchUnit en S17)
- Lee el `package-info.java` de un paquete antes de añadir clases en él.

## Código y tests
- Spring Boot 4 / Java 25: en tests usa `@MockitoBean` (no `@MockBean`) y `spring-boot-starter-webmvc-test` para `@WebMvcTest`.
- Value objects como `record` inmutables que validan en el constructor.
- Nombres de test: `should_<comportamiento>_when_<condición>` (ver "Estilo").
- Todo método público de un caso de uso en `application/` tiene test unitario.
- Integración con Testcontainers; NUNCA mocks de base de datos para verificar queries.

## Estilo (Checkstyle: `config/checkstyle.xml`, todas las reglas rompen el build)
El formato NO se discute ni se hace a mano: Spotless lo aplica al final de tu turno y en el pre-commit. Checkstyle comprueba el estilo tras cada edición y te devuelve archivo, línea y qué hacer.
- Inyección por constructor (campo `private final` + constructor); NUNCA `@Autowired` en campos de `src/main`. Motivo: dependencias explícitas, clases testeables sin Spring. (En tests de Spring sí se permite.)
- Logging con SLF4J; NUNCA `System.out`, `System.err` ni `printStackTrace()`. Motivo: la consola no tiene nivel, contexto ni correlación, y `printStackTrace` suele acompañar a una excepción tragada.
- NUNCA captures `Exception`, `RuntimeException` ni `Throwable`, ni dejes un `catch` vacío: captura la excepción concreta y tradúcela a una de dominio, o deja que se propague al manejador global. Motivo: el atajo típico "para que compile" esconde fallos.
- NUNCA desactives un test (`@Disabled` con o sin import, `@DisabledIf…`, `@DisabledOnOs`…). Motivo: poner el build en verde desactivando un test es bajar el umbral.
- Tests: `should_<comportamiento>_when_<condición>` en snake_case minúscula; la frase legible va en `@DisplayName` (que además traza el escenario de la spec). Motivo: el agente nombró 12 tests sin `_when_` en el lab S05.
- Métodos ≤ 40 líneas, ≤ 5 parámetros, complejidad ciclomática ≤ 10, archivos ≤ 500 líneas: extrae métodos con nombre o agrupa parámetros en un record. Motivo: el código generado tiende a resolverlo todo en un método largo con `if` anidados.
- `switch` sobre enums: usa una expresión `switch` (`case X -> ...`) para que el compilador exija cubrir todos los valores. `equals` siempre con `hashCode`. Sin imports con `*`.
- NUNCA añadas `@SuppressWarnings("checkstyle:...")` ni edites `config/checkstyle*.xml`: si una regla no aplica, detente y explica por qué; una persona decide la supresión y la justifica.

## Persistencia (JPA) — ADR `docs/decisiones/listado-de-pedidos.md`
- Todas las asociaciones son `fetch = FetchType.LAZY`; en `@ManyToOne`/`@OneToOne` escríbelo explícito (por defecto son EAGER). NUNCA `FetchType.EAGER`, `hibernate.enable_lazy_load_no_trans` ni `spring.jpa.open-in-view=true`. (Checkstyle `toOneLazy`/`noEagerFetch`)
- Qué se carga lo decide la consulta del caso de uso: listado → proyección (`select new …Row(...)`); detalle → `@EntityGraph` con una sola colección; volumen grande → batch fetching + paginación.
- Si aparece una `LazyInitializationException`, carga la asociación que nombra el mensaje en la consulta del caso de uso. NUNCA la resuelvas con EAGER, OSIV, `enable_lazy_load_no_trans`, `Hibernate.initialize()` en bucle ni consultas dentro de un bucle.
- NUNCA llames a un repositorio dentro de un bucle (`findById` por elemento): carga todos los ids de una vez (`findAllById`) o usa una proyección. El batch fetching NO agrupa esas llamadas.
- Los servicios devuelven DTOs o records de dominio, nunca entidades JPA.
- Todo endpoint de listado tiene un test de número de consultas en el fast loop (`*Test`): datos con MÁS filas que `default_batch_fetch_size` (p. ej. 120), `flush()` + `clear()`, `Statistics.clear()` y un umbral constante (el coste del código correcto, p. ej. `≤ 2`). El número de consultas no puede depender del número de filas.
- En proyecciones con `left join`, cuenta una columna no nula de la entidad unida (`count(l.productId)`), nunca la entidad o el embeddable (`count(l)`): las filas vacías del `left join` cuentan 1.
- Consultas paginadas con `join fetch`: `countQuery` explícito y test de `getTotalElements()`.
- `default_batch_fetch_size=50` es la red de seguridad del proyecto: no lo cambies (zona protegida).

## Seguridad
- NUNCA loguear Request/Response completos ni datos personales (email, documento, teléfono, tarjeta). (pendiente: Semgrep en S12)
- Secretos SOLO por variables de entorno (`${VARIABLE}`); NUNCA en código ni archivos versionados. (pendiente: Gitleaks en S12)
- NUNCA SQL concatenado; siempre parámetros. (pendiente: Semgrep en S12)

## Verify (no negociable)
- Antes de terminar cualquier tarea: `bash ./verify-quality.sh --fast` en verde y pega su salida completa.
- Si Verify falla: corrige la CAUSA indicada (archivo, línea, regla).
- PROHIBIDO para ponerlo en verde: desactivar reglas, bajar umbrales, `@Disabled`, borrar o debilitar tests, excluir archivos, `git commit --no-verify`.
- PROHIBIDO editar `config/`, `.claude/`, `.opencode/`, `.github/`, `pom.xml` (incluye dependencias nuevas), `.env*`, `verify-quality.sh`, `scripts/`, `lefthook.yml`, `openspec/config.yaml`, `src/main/resources/application*`. Si hace falta, detente y pide permiso a una persona.

## graphify
- Para preguntas sobre el código, consulta primero el grafo (sus nodos están en inglés: pregunta en inglés): `graphify query "<pregunta>"`, `graphify path "<A>" "<B>"`, `graphify explain "<concepto>"`. Lee `graphify-out/GRAPH_REPORT.md` solo para una visión general.
- Las aristas `INFERRED`/`AMBIGUOUS` son hipótesis, y un nodo sin aristas no prueba que no se use (inyección por tipo, YAML): confírmalo en el código antes de borrar nada.
- Tras modificar código, o si no encuentras algo recién creado, ejecuta `graphify update .` (sin coste de modelo).

## Especificaciones (OpenSpec)
- Todo cambio con comportamiento observable nuevo empieza con `/opsx:propose` (OpenCode: `/opsx-propose`). No implementes hasta que una persona haya revisado la propuesta.
- `openspec/specs/` y `openspec/changes/archive/` están protegidos: el comportamiento cambia con un delta en `openspec/changes/<nombre>/`.
- Nunca archives ni sincronices specs (`openspec archive`, `/opsx:sync`): lo hace una persona tras revisar el PR.
