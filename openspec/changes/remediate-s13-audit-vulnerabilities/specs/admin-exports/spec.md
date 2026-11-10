# Spec Delta

## Purpose

Permite descargar exports administrativos conservando las lecturas dentro de la carpeta autorizada y sin revelar archivos externos.

## ADDED Requirements

### Requirement: Lecturas de export confinadas a la carpeta autorizada
El sistema SHALL resolver los nombres de export dentro de una base absoluta normalizada y rechazar cualquier destino normalizado externo con HTTP 400 y ProblemDetail type `https://api.master-acdc.dev/problems/invalid-export-name`. SHALL no revelar contenido externo ni rutas internas y conservar permisos y descargas válidas existentes.

#### Scenario: Export rechaza ascenso al directorio padre
- **GIVEN** un archivo legible fuera de exports con contenido distintivo y un bearer ADMIN vigente
- **WHEN** solicita GET `/api/admin/exports` con `name=../<archivo>`
- **THEN** responde HTTP 400 con type `https://api.master-acdc.dev/problems/invalid-export-name`, sin el contenido ni la ruta interna
- **AND** archivos, cuentas, pedidos y stock no cambian

#### Scenario: Export rechaza escape con separadores del sistema
- **GIVEN** un archivo legible fuera de exports y un bearer ADMIN vigente
- **WHEN** solicita GET `/api/admin/exports` con un nombre relativo que escapa usando separadores nativos, incluido `..\<archivo>` en Windows
- **THEN** responde HTTP 400 con type `https://api.master-acdc.dev/problems/invalid-export-name`, sin el contenido ni la ruta interna
- **AND** archivos, cuentas, pedidos y stock no cambian

#### Scenario: Export rechaza ruta absoluta externa
- **GIVEN** un archivo legible externo y un bearer ADMIN vigente
- **WHEN** solicita GET `/api/admin/exports` con la ruta absoluta de ese archivo en `name`
- **THEN** responde HTTP 400 con type `https://api.master-acdc.dev/problems/invalid-export-name`, sin el contenido ni la ruta interna
- **AND** archivos, cuentas, pedidos y stock no cambian
