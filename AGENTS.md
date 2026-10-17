# AGENTS.md

Las reglas marcadas "(pendiente: … Sxx)" aún NO tienen verificador automático: cúmplelas igual y no las describas como verificadas.

## Comandos (tu shell en Windows es PowerShell: invoca los scripts con `bash`)
- Compilar y probar: `bash ./mvnw -q test`. Formato: `bash ./mvnw spotless:apply`.
- Verify: `bash ./verify-quality.sh --fast` (Spotless + tests).

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
- Nombres de test: `should_<comportamiento>_when_<condición>`. (pendiente: Checkstyle en S06)
- Todo método público de un caso de uso en `application/` tiene test unitario.
- Integración con Testcontainers; NUNCA mocks de base de datos para verificar queries.

## Seguridad
- NUNCA loguear Request/Response completos ni datos personales (email, documento, teléfono, tarjeta). (pendiente: Semgrep en S12)
- Secretos SOLO por variables de entorno (`${VARIABLE}`); NUNCA en código ni archivos versionados. (pendiente: Gitleaks en S12)
- NUNCA SQL concatenado; siempre parámetros. (pendiente: Semgrep en S12)

## Verify (no negociable)
- Antes de terminar cualquier tarea: `bash ./verify-quality.sh --fast` en verde y pega su salida completa.
- Si Verify falla: corrige la CAUSA indicada (archivo, línea, regla).
- PROHIBIDO para ponerlo en verde: desactivar reglas, bajar umbrales, `@Disabled`, borrar o debilitar tests, excluir archivos, `git commit --no-verify`.
- PROHIBIDO editar `config/`, `.claude/`, `.opencode/`, `.github/`, `pom.xml` (incluye dependencias nuevas), `.env*`, `verify-quality.sh`, `scripts/`, `lefthook.yml`, `openspec/config.yaml`. Si hace falta, detente y pide permiso a una persona.

## graphify
- Para preguntas sobre el código, consulta primero el grafo (sus nodos están en inglés: pregunta en inglés): `graphify query "<pregunta>"`, `graphify path "<A>" "<B>"`, `graphify explain "<concepto>"`. Lee `graphify-out/GRAPH_REPORT.md` solo para una visión general.
- Las aristas `INFERRED`/`AMBIGUOUS` son hipótesis, y un nodo sin aristas no prueba que no se use (inyección por tipo, YAML): confírmalo en el código antes de borrar nada.
- Tras modificar código, o si no encuentras algo recién creado, ejecuta `graphify update .` (sin coste de modelo).

## Especificaciones (OpenSpec)
- Todo cambio con comportamiento observable nuevo empieza con `/opsx:propose` (OpenCode: `/opsx-propose`). No implementes hasta que una persona haya revisado la propuesta.
- `openspec/specs/` y `openspec/changes/archive/` están protegidos: el comportamiento cambia con un delta en `openspec/changes/<nombre>/`.
- Nunca archives ni sincronices specs (`openspec archive`, `/opsx:sync`): lo hace una persona tras revisar el PR.
