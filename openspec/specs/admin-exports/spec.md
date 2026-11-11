# admin-exports Specification

## Purpose
Permite a administración descargar archivos de exportación ya preparados en una carpeta local, sin añadir generación de informes ni mecanismos de subida.

## Requirements

### Requirement: Descarga administrativa de un export existente
El sistema SHALL atender GET `/api/admin/exports?name={name}` con query param obligatorio `name` para ADMIN con bearer vigente y devolver HTTP 200 con los bytes del archivo solicitado y tipo `application/octet-stream`. SHALL leer desde la carpeta local de exports sin modificar el archivo. La autorización SHALL seguir la restricción administrativa vigente.

#### Scenario: Administrador descarga un export existente
- **GIVEN** un archivo `pedidos.csv` existente en la carpeta de exports
- **WHEN** ADMIN solicita GET `/api/admin/exports?name=pedidos.csv` con bearer vigente
- **THEN** responde HTTP 200 con `application/octet-stream` y exactamente los bytes del archivo
- **AND** no modifica archivos, cuentas, pedidos ni stock

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
