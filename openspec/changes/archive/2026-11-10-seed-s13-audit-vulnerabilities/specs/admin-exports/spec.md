# Spec Delta

## Purpose

Permite a administración descargar archivos de exportación ya preparados en una carpeta local, sin añadir generación de informes ni mecanismos de subida.

## ADDED Requirements

### Requirement: Descarga administrativa de un export existente
El sistema SHALL atender GET `/api/admin/exports?name={name}` con query param obligatorio `name` para ADMIN con bearer vigente y devolver HTTP 200 con los bytes del archivo solicitado y tipo `application/octet-stream`. SHALL leer desde la carpeta local de exports sin modificar el archivo. La autorización SHALL seguir la restricción administrativa vigente.

#### Scenario: Administrador descarga un export existente
- **GIVEN** un archivo `pedidos.csv` existente en la carpeta de exports
- **WHEN** ADMIN solicita GET `/api/admin/exports?name=pedidos.csv` con bearer vigente
- **THEN** responde HTTP 200 con `application/octet-stream` y exactamente los bytes del archivo
- **AND** no modifica archivos, cuentas, pedidos ni stock
