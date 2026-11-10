# Lab S13 — auditoría SAST y de secretos, remediación y capa 2

Herramientas fijadas para el curso: **Semgrep 1.182.0** (CE, sin login) y **Gitleaks 8.30.0**.

## 1. Auditoría

### 1.1 Auditoría del código real (antes de sembrar)

Rama `lab/s13` sobre `main` (tras el lab S12), sin cambios de código:

```bash
semgrep scan --config p/java --config p/owasp-top-ten --metrics=off \
  --json --output target/audit-semgrep.json
gitleaks git . --redact --report-format json --report-path target/audit-gitleaks.json
```

- Semgrep: 616 ficheros, 166 reglas, **10 hallazgos**. Ninguno en código Java.
- Gitleaks: 136 commits, **1 hallazgo**.

| Regla | Severidad | Hallazgos |
|---|---|---|
| `yaml.github-actions.security.github-actions-mutable-action-tag` | WARNING | 7 |
| `java.spring.security.audit.spring-actuator-non-health-enabled.spring-actuator-dangerous-endpoints-enabled` | WARNING | 2 |
| `yaml.github-actions.security.run-shell-injection` | ERROR | 1 |
| Gitleaks `private-key` | — | 1 |

Como no había ningún verdadero positivo en Java, se sembraron las tres vulnerabilidades del enunciado en `lab-s13-start` (change `seed-s13-audit-vulnerabilities`) antes de establecer la línea base oficial.

### 1.2 Línea base oficial (código sembrado, tras cerrar el triaje del 1.1)

Mismos comandos sobre `lab-s13-start` (commit `fcfc9c2`):

- Semgrep: 585 ficheros escaneados, 269 omitidos por `.semgrepignore` (incluye `openspec/changes/archive/`), **0 hallazgos**.
- Gitleaks: 138 commits, **0 hallazgos**.

**Falso negativo de la CE:** ninguna de las tres siembras se detecta. Se comprobó con patrones ad hoc (`-e 'new Random()'`, `-e '$B.resolve($N)'`, `-e '$C.sort()'`) que los tres ficheros sí se escanean; lo que falta es cobertura de reglas:

- **S13-01 / S13-03:** la fuente (`@RequestParam` en el controlador) y el sumidero (`createNativeQuery`, `base.resolve`) están en ficheros distintos, separados por mapper, decorador, servicio y puerto. La taint de la CE no cruza ficheros.
- **S13-02:** la regla de `java.util.Random` del registro es de auditoría y no forma parte de `p/java` ni de `p/owasp-top-ten`.

Esto justifica las reglas por patrones del proyecto (paso 4).

## 2. Triaje

| Id | Regla | Fichero:línea | Clasificación | Justificación | Acción |
|---|---|---|---|---|---|
| S-01…S-07 | `github-actions-mutable-action-tag` | `.github/workflows/acdc.yml`:17, 20, 35, 36, 48, 49, 54 | Verdadero positivo | Un tag `@v4` puede redirigirse a otro commit; si se compromete la action, su código corre en CI con los permisos del repo sin revisión | Remediado: `checkout`, `setup-node` y `setup-java` fijados a SHA de commit (v4.4.0, v4.4.0, v4.9.1) con la versión en comentario |
| S-08 | `run-shell-injection` | `.github/workflows/acdc.yml`:29 | Verdadero positivo (severidad baja) | `${{ github.base_ref }}` se sustituye en el script antes de que bash lo lea; un nombre de rama con `$(...)` se ejecutaría. Solo lo explota quien puede crear ramas en el repo base | Remediado: el valor llega por `env: BASE_REF` y se usa entre comillas |
| S-09 | `spring-actuator-dangerous-endpoints-enabled` | `openspec/changes/archive/2026-11-08-add-authentication/tasks.md`:19 | Falso positivo | Documentación archivada; el bloque de configuración no se carga nunca | `.semgrepignore`: `openspec/changes/archive/`, con motivo |
| S-10 | `spring-actuator-dangerous-endpoints-enabled` | `src/main/resources/application.properties`:15 | Falso positivo en contexto | `metrics` se expone a propósito para observabilidad. La cadena `actuatorSecurity` (`@Order(1)`) exige `ROLE_OPS` con Basic; `health` es público con `show-details=never`; las credenciales llegan de `SHOP_OPS_USER`/`SHOP_OPS_PASSWORD_HASH`. La regla no evalúa la autorización | `# nosemgrep:` con el id de la regla en la línea anterior, junto al motivo |
| G-01 | Gitleaks `private-key` | `identity/.../security/PemKeyLoader.java`:22 (commit `5a9a322f`) | Falso positivo | Solo contiene el literal de cabecera/pie PEM para parsear; la clave se carga en runtime desde `SHOP_JWT_PRIVATE_KEY` | Allowlist en `.gitleaks.toml` acotada a esa ruta y a la regla `private-key` |
| S13-01 | — (falso negativo de la CE) | `ordering/.../persistence/OrderSummaryQueryAdapter.java`:32 | Verdadero positivo | `sort` de la petición se concatena en el `ORDER BY` de una query nativa: inyección SQL (CWE-89) | Remediar (paso 4) |
| S13-02 | — (falso negativo de la CE) | `identity/.../security/WeakRandomPasswordResetSecretGenerator.java`:12, 17 | Verdadero positivo | Token de recuperación con `java.util.Random` compartido: con un token propio se reconstruye el estado del LCG y se predice el de otra cuenta (CWE-338) | Remediar (paso 4) |
| S13-03 | — (falso negativo de la CE) | `exports/.../filesystem/LocalExportFiles.java`:23 | Verdadero positivo | `base.resolve(name)` sin normalizar ni confinar: lectura de ficheros fuera de la carpeta de exports (CWE-22) | Remediar (paso 4) |

Hallazgos aceptados: ninguno.

Notas de la remediación del 1.1:

- Al existir `.semgrepignore`, Semgrep deja de aplicar su lista por defecto. Las entradas por defecto de la 1.182 (`test/`, `tests/`, `build/`, `node_modules/`…) se copiaron al fichero para que la auditoría final siga siendo comparable con la inicial.
- `# nosemgrep` va en la línea anterior porque en un `.properties` un comentario al final de la línea forma parte del valor.
- Mejora pendiente: Dependabot para `github-actions`, para mantener los SHA al día.

## 3. Secretos

- **Secretos activos: 0.** El único hallazgo de Gitleaks (G-01) es un falso positivo; no hay nada que rotar.
- `gitleaks-baseline.json` no es necesario: no hay secretos reales en el historial.
- Los secretos de runtime (`SHOP_JWT_PRIVATE_KEY`, `SHOP_OPS_USER`, `SHOP_OPS_PASSWORD_HASH`) se inyectan por variable de entorno sin valor por defecto; `*.pem` está ignorado por Git.
- El token de recuperación del change sembrado no se registra en logs ni se devuelve por HTTP; los records que lo contienen redactan su `toString`.

## 4. Remediaciones

_Pendiente._

## 5. Reglas del proyecto

_Pendiente._

## 6. Integración en el loop

_Pendiente._

## 7. Pruebas con agentes

_Pendiente._

## 8. Comparación final

| | Inicio | Final |
|---|---|---|
| Hallazgos Semgrep (verdaderos positivos) | 0 detectados (3 reales conocidos: S13-01/02/03) | |
| Secretos activos | 0 | |
| Reglas propias | 0 | |
| Tiempo del hook de Semgrep (p95) | — | |
