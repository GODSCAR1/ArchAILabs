# Fallos: secciones 4 y 5 de RBAC

## Base (antes de editar, 2026-11-09, rama lab/s12)

- Hallazgos abiertos declarados: ninguno.
- `bash ./verify-quality.sh --fast`: correcto, ningún fallo.
- `bash ./mvnw -q verify`: correcto, ningún fallo (repetido con timeout de
  600 s tras agotar los 200 s del primer intento).
- Lista base de fallos: `[]`.
- PIT no medido; umbral de referencia 75 %.

## Resultado final

- `bash ./mvnw spotless:apply`: correcto.
- `openspec validate add-rbac-and-object-authorization --strict --no-interactive`:
  change válido.
- `bash ./verify-quality.sh --fast`: correcto.
- `bash ./mvnw -q verify`: correcto, incluidas las carreras PostgreSQL y suites
  administrativas. Se usó `JAVA_TOOL_OPTIONS="-Dlogging.level.root=OFF
  -Dspring.main.banner-mode=off"` en ambas verificaciones finales para reducir
  salida de arranque, sin cambiar selección de tests ni assertions.
- Lista final de fallos: `[]`.

## Diff frente a la base

```json
{"base": [], "final": [], "nuevos": [], "resueltos": [], "persistentes": []}
```

Las tareas 4.1–4.3 y 5.1–5.3 quedan completas (22/27 en el change).
PIT no medido; umbral de referencia 75 %, sin cambios de configuración.

`graphify update .` actualizó el grafo de código: 3235 nodos, 7340 aristas.
Conserva los avisos de parser SQL ausente, dos archivos de configuración sin
nodos y etiquetas de comunidades por refrescar; no acredita extracción SQL ni
actualización semántica de documentación.
