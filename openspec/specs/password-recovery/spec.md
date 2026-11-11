# password-recovery Specification

## Purpose
Permite recuperar el acceso a una cuenta mediante una solicitud y una confirmación con token, usando una notificación local de laboratorio sin correo real ni exposición del secreto por HTTP.

## Requirements

### Requirement: Solicitud de recuperación con notificación privada
El sistema SHALL aceptar POST `/api/auth/password-reset/request` con `{email}` sin bearer y responder HTTP 202 sin cuerpo. Para una cuenta habilitada SHALL enviar el token únicamente al canal de notificación, sin incluirlo en respuestas ni logs. La respuesta SHALL ser la misma para un email válido sin cuenta o de cuenta deshabilitada, sin enviar notificación en esos casos.

#### Scenario: Solicitud de recuperación notifica al destinatario
- **GIVEN** una cuenta habilitada con email `cliente@example.com`
- **WHEN** se solicita recuperación con ese email sin bearer ni cookies
- **THEN** responde HTTP 202 sin cuerpo y el canal de notificación recibe el email y un token utilizable
- **AND** la contraseña, versión de sesión, familias, pedidos y stock no cambian

### Requirement: Confirmación de recuperación con nueva contraseña
El sistema SHALL aceptar POST `/api/auth/password-reset/confirm` con `{email,token,newPassword}` sin bearer. Con token vigente del destinatario SHALL responder HTTP 204 sin cuerpo, cambiar la contraseña según la política vigente y consumir el token en la misma operación. El token SHALL vencer a los 15 minutos y servir una sola vez. La API SHALL no emitir access tokens ni cookies de autenticación al recuperar la contraseña.

#### Scenario: Recuperación permite iniciar sesión con la nueva contraseña
- **GIVEN** una cuenta habilitada y su token vigente obtenido del canal de notificación
- **WHEN** confirma con el email, ese token y una contraseña nueva válida
- **THEN** responde HTTP 204 sin cuerpo ni credenciales y consume el token
- **AND** un POST `/api/auth/login` posterior con la nueva contraseña responde HTTP 200
- **AND** pedidos y stock no cambian

### Requirement: Recuperación conserva revocación de sesiones
El sistema SHALL incrementar la versión de sesión y revocar todas las familias de la cuenta al confirmar una recuperación. SHALL publicar la barrera compartida antes del commit y responder éxito solo tras confirmar la operación, conservando el contrato vigente de revocación inmediata.

#### Scenario: Recuperación actualiza credenciales y estado de sesión conjuntamente
- **GIVEN** una cuenta habilitada con familias activas y token de recuperación vigente
- **WHEN** confirma la recuperación con una contraseña válida
- **THEN** responde HTTP 204, persiste el nuevo hash y la siguiente versión de sesión, revoca sus familias y la barrera compartida refleja esa versión
- **AND** las cuentas y familias de otros usuarios, pedidos y stock no cambian

### Requirement: Secreto de recuperación impredecible entre cuentas
El sistema SHALL generar secretos de recuperación con 256 bits de aleatoriedad criptográfica. Observar el secreto de una cuenta SHALL no permitir predecir el siguiente secreto de otra cuenta. SHALL conservar notificación privada, hash, expiración, consumo único y revocación vigentes.

#### Scenario: Token propio no permite predecir el siguiente token ajeno
- **GIVEN** dos cuentas habilitadas y solicitudes consecutivas sin generaciones intercaladas
- **WHEN** ambas solicitan POST `/api/auth/password-reset/request`, primero la cuenta propia y después la otra
- **THEN** ambas reciben HTTP 202 sin cuerpo ni secretos
- **AND** la predicción obtenida reconstruyendo el estado del generador débil desde el token propio no coincide con el token real de la otra cuenta, observado solo por el canal privado de prueba
- **AND** contraseñas, versiones de sesión, familias, pedidos y stock no cambian
