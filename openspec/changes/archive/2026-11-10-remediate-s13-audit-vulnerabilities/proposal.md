# Proposal

## Why

La línea base de `lab/s13` contiene S13-01 (SQLi), S13-02 (tokens predecibles) y S13-03 (path traversal), sembradas por `seed-s13-audit-vulnerabilities`. Los perfiles públicos de auditoría no detectan ninguna; los pasos 4 y 5 del laboratorio necesitan corregir sus causas y añadir cobertura local reproducible.

## What Changes

- Demostrar y remediar S13-01, S13-02 y S13-03, en ese orden: test rojo ejecutado → parada para commit humano → nueva petición para corregir → verde → regla propia con fixtures positivos y negativos.
- **BREAKING**: `sort` deja de admitir expresiones arbitrarias. Solo se admiten `id`, `customerName`, `lineCount`, `totalUnits` y dirección ASC/DESC; se conserva `totalUnits DESC` y el desempate final por id.
- Retirar la excepción SQL temporal de la siembra: restaurar consulta tipada, proyección y filtros por alcance sin SQL concatenado.
- Generar tokens de recuperación con 32 bytes criptográficamente seguros, renombrando el adaptador débil y preservando el contrato de recuperación.
- Rechazar con HTTP 400 los nombres de export que salen de la carpeta base normalizada, incluidas rutas absolutas externas.
- Crear reglas locales para las tres causas y las cuatro reglas adicionales solicitadas; excluir sus fixtures de la auditoría ordinaria con motivo, conservando su ejecución mediante `semgrep --test semgrep-rules/`.
- Completar `docs/lab-s13.md`, secciones 4 y 5, con evidencia real al implementar.

## Capabilities

### New Capabilities

- `password-recovery`: añadir el requisito de impredecibilidad entre cuentas; reutiliza la ruta ya propuesta por la siembra, aún no sincronizada a specs principales.
- `admin-exports`: añadir confinamiento de lectura; reutiliza la ruta ya propuesta por la siembra, aún no sincronizada.
- `static-security-analysis`: reglas locales de seguridad con fixtures ejecutables y auditoría del código corregido.

### Modified Capabilities

- `ordering`: añadir ordenación restringida y rechazo de inyección, preservando alcance, métricas, clientes huérfanos y rendimiento.

## Impact

- `ordering`: criterio de listado y validación, mapper/advice web, consulta y mapper de persistencia; pruebas HTTP/PostgreSQL y guardianes existentes.
- `identity`: generador de recuperación, ensamblaje y tests, sin cambiar la seguridad de refresh, JWT o revocación.
- `exports`: adaptador de ficheros, error/advice y pruebas HTTP y unitarias.
- `semgrep-rules/`, `.semgrepignore` y documentación del lab. Sin dependencias nuevas ni cambios de configuración protegida.
- Dependencia de comportamiento: la siembra está implementada pero sus deltas siguen activos. Estos deltas añaden restricciones bajo las mismas rutas y no archivan ni sincronizan el change anterior.

## Non-goals

- Integración del escáner en hooks/CI, medición p95, Dependabot o pasos 6–8 del laboratorio.
- Nuevos endpoints, permisos, cambios de matriz protegida, reglas JWT/CSRF, migraciones o cambios de TTL.
- Remediar otros tipos de hallazgos o implementar durante esta propuesta.
