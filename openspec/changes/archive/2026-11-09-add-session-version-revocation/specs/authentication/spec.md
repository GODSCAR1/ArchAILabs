# Spec Delta

## MODIFIED Requirements

### Requirement: Access tokens verificables y mínimos
El sistema SHALL emitir JWT RS256 con vida de 900 segundos, header kid igual al thumbprint RFC 7638 de la pública, y exactamente los claims iss, sub, aud, iat, exp, jti, roles y sv. sub SHALL ser el UUID de la cuenta y jti único. sv SHALL ser la versión de sesión vigente al emitir bajo exclusión mutua de la cuenta. SHALL validar firma RS256, selección por kid, vigencia, issuer y audience configurados, sin normalizar sus barras finales.

#### Scenario: Claims exactos del access token
- **WHEN** se obtiene un access token por login o refresh válido
- **THEN** responde HTTP 200 y el JWT contiene exactamente los ocho claims iss, sub, aud, iat, exp, jti, roles, sv; exp menos iat es 900, roles contiene CUSTOMER, sub es el id del registro y sv es un número entero igual a la versión vigente de la cuenta
- **AND** no hay email, permisos ni customerId

#### Scenario: Token inválido no autentica
- **WHEN** se llama `GET /api/orders?status=PAID` con un JWT con otra aud, otro iss (incluida barra final distinta), caducado, firmado por otra clave, algoritmo distinto o kid desconocido
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized` y challenge Bearer; pedidos y stock permanecen sin cambios

### Requirement: Cambio de contraseña revoca todas las familias
El sistema SHALL aceptar `POST /api/users/me/password` autenticado con `{currentPassword,newPassword}`, comprobar la contraseña actual y aplicar la política de nueva contraseña. SHALL cambiar el hash, incrementar la versión de sesión y revocar todas las familias del usuario atómicamente, respondiendo 204 solo cuando los access tokens anteriores ya no autentiquen.

#### Scenario: Cambio de contraseña cierra renovación de otro dispositivo
- **WHEN** un usuario con dos familias cambia correctamente su contraseña
- **THEN** responde HTTP 204 y refresh desde cualquiera de ambos dispositivos responde HTTP 401 con type `https://api.master-acdc.dev/problems/invalid-refresh-token`
- **AND** usar cualquiera de sus access tokens previos en `GET /api/orders?status=PAID` responde inmediatamente HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized` y challenge Bearer
- **AND** las familias de otros usuarios, los pedidos y el stock permanecen sin cambios

#### Scenario: Contraseña actual incorrecta
- **WHEN** un usuario autenticado intenta cambiar contraseña con currentPassword incorrecta
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/invalid-credentials`, sin cambiar contraseña, versión, familias, pedidos ni stock

#### Scenario: Login posterior usa la nueva versión
- **WHEN** tras cambiar correctamente la contraseña se inicia sesión con la nueva contraseña y se renueva esa nueva familia
- **THEN** ambos POST responden HTTP 200 y sus access tokens contienen los ocho claims con la nueva versión; `GET /api/orders?status=PAID` con esos tokens responde HTTP 200
- **AND** la contraseña anterior obtiene HTTP 401 con type `https://api.master-acdc.dev/problems/invalid-credentials`, sin crear familia ni modificar pedidos o stock

## ADDED Requirements

### Requirement: Autenticación exige versión de sesión coincidente
El sistema SHALL comprobar sv contra la versión vigente en cada autenticación bearer. SHALL rechazar sv ausente, no numérico, fraccionario, negativo, fuera del rango entero soportado o distinto de la versión vigente, sin convertir cadenas numéricas en números. SHALL rechazar cuentas inexistentes. Un token rechazado SHALL no ejecutar la operación protegida.

#### Scenario: Token sin versión no autentica
- **WHEN** se solicita `GET /api/orders?status=PAID` con un token de firma, tiempos, roles, issuer y audience válidos pero sin sv
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized` y challenge Bearer; cuentas, familias, pedidos y stock permanecen sin cambios

#### Scenario: Versión no numérica no autentica
- **WHEN** se solicita `GET /api/orders?status=PAID` con un token válido salvo por sv textual, incluido `"0"`, booleano, objeto, array, fraccionario, negativo o fuera de rango
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized` y challenge Bearer; cuentas, familias, pedidos y stock permanecen sin cambios

#### Scenario: Versión distinta o cuenta inexistente no autentica
- **WHEN** se solicita `GET /api/orders?status=PAID` con un token por lo demás válido cuyo sv es inferior o superior a la versión vigente, o cuya cuenta no existe
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized` y challenge Bearer; cuentas, familias, pedidos y stock permanecen sin cambios

### Requirement: Comprobación compartida de versión falla cerrada
La comprobación SHALL consultar el almacén compartido por usuario. Si la clave falta, SHALL consultar la fuente persistente y poblarla de forma coordinada con las revocaciones. Timeout, desconexión o valor corrupto SHALL rechazar el token con 401 y WARN sin tokens ni datos personales. SHALL no aceptar desde memoria local ni eludir la comprobación por un error de infraestructura.

#### Scenario: Clave ausente se reconstruye sin resucitar sesiones
- **WHEN** una cuenta habilitada presenta su access token vigente en `GET /api/orders?status=PAID` sin clave compartida y ambas dependencias responden
- **THEN** responde HTTP 200 y las comprobaciones posteriores usan la versión compartida reconstruida desde el origen persistente

#### Scenario: Almacén compartido caído rechaza y registra
- **WHEN** se presenta un access token vigente en `GET /api/orders?status=PAID` y la lectura compartida falla por timeout o conexión
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized` y challenge Bearer, registra WARN sin bearer, refresh, contraseñas, email u otros datos personales y no acepta mediante fallback
- **AND** cuentas, familias, pedidos y stock permanecen sin cambios

#### Scenario: Fallo al poblar clave no autentica
- **WHEN** se solicita `GET /api/orders?status=PAID` con token vigente y clave ausente, pero falla la escritura compartida después de leer el origen
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized` y challenge Bearer, registra WARN sin secretos ni datos personales; cuentas, familias, pedidos y stock permanecen sin cambios

#### Scenario: Valor compartido corrupto no autentica
- **WHEN** se solicita `GET /api/orders?status=PAID` con token vigente y el valor de sesión compartido no es una versión válida
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized` y challenge Bearer, registra WARN sin secretos ni datos personales; cuentas, familias, pedidos y stock permanecen sin cambios

### Requirement: Revocación no deja una ventana de aceptación antigua
Una revocación confirmada SHALL ser efectiva antes de responder éxito. SHALL no confirmar cambios de contraseña o bloqueo si no puede establecer previamente la barrera compartida. Un fallo posterior a establecerla SHALL conservar la denegación conservadora sin restaurar una versión anterior. La carga concurrente de una clave ausente SHALL no publicar una versión obsoleta después de la revocación.

#### Scenario: Fallo compartido durante escritura impide confirmar contraseña
- **WHEN** un cambio de contraseña ya autenticado intenta publicar la nueva versión y falla la escritura compartida, incluso con resultado de escritura incierto
- **THEN** POST `/api/users/me/password` responde HTTP 503 con type `https://api.master-acdc.dev/problems/session-version-unavailable` y Retry-After: 1; contraseña, versión persistente, familias, pedidos y stock permanecen sin cambios
- **AND** si la escritura llegó a aplicarse, no se deshace la barrera; durante la indisponibilidad las llamadas bearer responden HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`

#### Scenario: Rollback posterior a la barrera no restaura sesiones
- **WHEN** falla la confirmación persistente de un cambio de contraseña después de publicar la nueva versión
- **THEN** POST `/api/users/me/password` responde HTTP 503 con type `https://api.master-acdc.dev/problems/session-version-unavailable` y Retry-After: 1, sin confirmar contraseña, versión persistente ni familias y sin modificar pedidos o stock
- **AND** mientras esa barrera siga presente, los tokens anteriores obtienen HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`; no se restaura automáticamente la versión compartida anterior

#### Scenario: Carga concurrente no sobrescribe la revocación
- **WHEN** una reconstrucción de clave compite con un cambio correcto de contraseña
- **THEN** el cambio responde HTTP 204 y toda llamada posterior a `GET /api/orders?status=PAID` con el token anterior responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized`, sin cambios en pedidos o stock

### Requirement: Coste acotado de comprobación de sesión
Con clave presente, cada autenticación bearer válida SHALL realizar una sola lectura compartida de versión y ninguna consulta persistente adicional de versión, independientemente del número de recursos devueltos. La latencia añadida SHALL medirse y documentarse distinguiendo acierto, reconstrucción y dependencia indisponible, sin inventar resultados.

#### Scenario: Clave presente evita consultas repetidas de versión
- **WHEN** se ejecutan peticiones autenticadas repetidas de `GET /api/orders?status=PAID` con clave presente y un listado de 120 pedidos
- **THEN** cada petición responde HTTP 200 con una lectura compartida de versión y cero consultas persistentes de versión, manteniendo el presupuesto constante de consultas de negocio; pedidos y stock permanecen sin cambios
