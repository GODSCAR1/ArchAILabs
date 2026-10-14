# AGENTS.md

Reglas para cualquier colaborador (humano o agente). Léelas antes de proponer cambios.
Cada regla indica quién la verifica; las marcadas "desde Sxx" se exigirán a partir de esa sección.

## Arquitectura (hexagonal estricta)
- `domain/` NUNCA importa Spring, JPA ni Jackson (`@Entity`, `@Repository`, `@Service`, `@Component` prohibidos ahí). Verificado con ArchUnit (desde S17).
- `application/` depende solo de `domain/`; NUNCA de `infrastructure/`. Verificado con ArchUnit (desde S17).
- Puertos de salida en `domain/port/` (solo interfaces); implementaciones en `infrastructure/adapters/`.
- Los beans se ensamblan en `infrastructure/config/` con `@Bean`. Sin lógica de negocio en métodos `@Bean`.
- NUNCA `ApplicationContext.getBean()` ni campos estáticos para acceder a beans fuera de `infrastructure/config/`. Verificado con ArchUnit (desde S17).
- Lee el `package-info.java` de un paquete antes de añadir clases en él.

## Estilo de código
- Formato con Spotless (Google Java Format). No discutas formato: ejecuta `./mvnw spotless:apply`. Verificado con `spotless:check` (pre-commit y Verify).
- Inyección SIEMPRE por constructor. NUNCA `@Autowired` en campos. Verificado con Checkstyle (desde S06).
- Value objects como `record` inmutables que validan en el constructor.
- Nombres de test: `should_<comportamiento>_when_<condición>`. Verificado con Checkstyle (desde S06).

## Testing
- Todo método público de un caso de uso en `application/` tiene test unitario.
- Mutation score mínimo (PIT): 75 % en `domain/`. Verificado con PIT `mutationThreshold` (desde S14).
- Integración con Testcontainers; NUNCA mocks de base de datos para verificar queries.
- NUNCA `@Disabled`, NUNCA borrar ni debilitar un test para poner el build en verde.

## Seguridad
- NUNCA loguear Request/Response completos ni datos personales (email, documento, teléfono, tarjeta). Verificado con Semgrep (desde S12).
- Secretos SOLO por variables de entorno (`${VARIABLE}`); NUNCA en código, `application.yml` ni archivos versionados. Verificado con Gitleaks (desde S12).
- NUNCA SQL concatenado; siempre parámetros. Verificado con Semgrep (desde S12).
- Toda dependencia nueva pasa OWASP Dependency-Check (desde S28) y la aprueba una persona.

## Verify (no negociable)
- Antes de terminar cualquier tarea: `./verify-quality.sh --fast` en verde.
- Si Verify falla: corrige la CAUSA indicada (archivo, línea, regla).
- PROHIBIDO para "arreglar" un fallo: desactivar reglas, bajar umbrales, añadir `@Disabled`, borrar o debilitar tests, excluir archivos.
- PROHIBIDO editar: `config/`, `.claude/`, `.opencode/`, `.github/`, `pom.xml`, `.env*`, `verify-quality.sh`. Si hace falta, detente y pide permiso a una persona.
- PROHIBIDO `git commit --no-verify` y desactivar hooks.