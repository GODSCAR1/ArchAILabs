# Proposal

## Why

La auditoría inicial del laboratorio S13 no encontró verdaderos positivos en Java. Se necesitan tres vulnerabilidades intencionadas y de categorías distintas en `lab-s13-start` para practicar el triaje con Semgrep y su posterior remediación con tests rojo→verde en otro change.

## What Changes

- Añadir `sort` a `GET /api/orders?status=...` y trasladarlo sin lista blanca hasta una consulta SQL nativa cuyo `ORDER BY` se construye por concatenación. Se usa pedidos porque el buscador de productos está en MongoDB y V7 retiró el catálogo PostgreSQL; alternativa confirmada por el usuario.
- Añadir solicitud y confirmación de recuperación bajo `/api/auth/password-reset/`, con generación débil del token mediante una única instancia de `java.util.Random` compartida por todas las generaciones del bean, y notificación mediante puerto de salida y adaptador en memoria. El token no se devuelve por HTTP ni se registra en logs.
- Añadir `GET /api/admin/exports?name={name}` para descargar un archivo local mediante una ruta construida sin normalización ni comprobación de pertenencia a la carpeta base. El nombre llega por query param sin cambiar el firewall; la autorización ADMIN sigue la ADR 0011.
- Registrar en el diseño las tres siembras, sus ubicaciones previstas y la excepción temporal del listado. Las líneas reales se completarán tras implementar y formatear.
- Preparar tareas humanas previas para abrir exactamente las rutas públicas y ampliar la matriz protegida. La adaptación humana H3 del constructor usado por el guardián de consultas está pendiente y se realizará inmediatamente antes de `/opsx-apply`, conservando 120 pedidos y el umbral ≤ 2; la nueva firma se integra durante apply, sin sobrecargas de compatibilidad.
- Limitar los tests nuevos del agente al camino feliz; la ampliación humana de la matriz preserva el contrato de acceso existente. Los tests que explotan las tres vulnerabilidades pertenecen al siguiente change.

## Capabilities

### New Capabilities

- `password-recovery`: solicitud, notificación y confirmación de recuperación sin credenciales previas.
- `admin-exports`: descarga administrativa de archivos ya existentes en la carpeta de exports.

### Modified Capabilities

- `ordering`: ordenación solicitada por el cliente en el listado de pedidos.
- `api-security`: dos excepciones públicas exactas para recuperación.
- `authorization`: extensión de la matriz de acceso para recuperación y exports.

## Impact

- `ordering`: controlador, mapper web, criterio, decorador de autorización, adaptador SQL, mapper de persistencia y callers/tests del criterio.
- `identity`: nuevos casos de uso y puertos, adaptadores de token/notificación/almacenamiento, controlador/mapper, manejo de errores y ensamblado transaccional reutilizando la revocación de sesiones vigente.
- Nuevo módulo hexagonal `exports` para lectura local; sin dependencias de negocio hacia otros módulos.
- PostgreSQL sigue siendo dueño de pedidos e identidad. No se modifica MongoDB, el catálogo ni la cache, ni se añaden dependencias.
- Ediciones humanas pendientes: `HttpSecurityConfiguration`, `SecurityMatrixTest`/`SecurityMatrixSupport` y adaptación del guardián de consultas (H3), esta última inmediatamente antes de `/opsx-apply`.

## Non-goals

- Corregir, mitigar o neutralizar las tres siembras durante este change.
- Añadir pruebas de explotación, reglas Semgrep, configuración Gitleaks, secretos sembrados o nuevas categorías de vulnerabilidad.
- Implementar correo real, publicar el buzón en memoria por HTTP, generar exports o permitir su subida.
- Cambiar permisos de `Role`, JWT, CSRF de refresh/logout, política de contraseñas o contrato de revocación de sesiones.
- Archivar/sincronizar specs o implementar código durante esta propuesta.
