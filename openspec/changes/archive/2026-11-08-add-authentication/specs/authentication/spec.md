# Spec Delta

## Purpose

Gestiona la identidad de usuarios y el ciclo de credenciales de acceso y renovación, con registro atómico, privacidad y detección de reutilización.

## ADDED Requirements

### Requirement: Registro atómico de una identidad de cliente
El sistema SHALL aceptar `POST /api/auth/register` con `{email,password,name?}`, normalizar email a minúsculas y crear conjuntamente una cuenta habilitada CUSTOMER y su cliente. SHALL responder 201 con únicamente `{id}` de la cuenta, sin cabecera Location, tokens ni customerId. Si falta name SHALL usar la parte local del email.

#### Scenario: Registro con nombre explícito
- **WHEN** se registra un email nuevo con nombre y contraseña válida
- **THEN** responde HTTP 201 con solo `{id}`, sin cabecera Location, tokens ni customerId, y existen cuenta y cliente vinculados con el nombre indicado

#### Scenario: Registro sin nombre
- **WHEN** se registra `Cliente@Example.com` sin name y con contraseña válida
- **THEN** responde HTTP 201 con solo `{id}` y sin cabecera Location, conserva el email `cliente@example.com` y el nombre de cliente `cliente`

#### Scenario: Registro duplicado no crea cliente huérfano
- **WHEN** se registra un email que ya existe, aunque cambie su capitalización o compita con otro registro
- **THEN** responde HTTP 409 con type `https://api.master-acdc.dev/problems/email-already-registered` y no crea cuenta ni cliente adicional; pedidos y stock permanecen sin cambios

### Requirement: Validación de credenciales nuevas
El sistema SHALL exigir email válido y contraseña nueva de al menos 12 caracteres, no incluida en la lista de contraseñas comunes, tanto al registrar como al cambiar contraseña. SHALL rechazar los datos inválidos sin modificar credenciales ni familias. Si se facilita name SHALL ser no vacío y tener como máximo 120 caracteres.

#### Scenario: Contraseña corta o común
- **WHEN** se registra una cuenta o se solicita con credenciales válidas cambiar la contraseña por una de menos de 12 caracteres o incluida en la lista común
- **THEN** responde HTTP 400 con type `https://api.master-acdc.dev/problems/invalid-password`; no cambia cuentas, clientes ni familias, y pedidos y stock permanecen sin cambios

#### Scenario: Datos de registro inválidos
- **WHEN** se registra con email inválido o nombre explícito vacío o mayor que 120 caracteres
- **THEN** responde HTTP 400 con type `https://api.master-acdc.dev/problems/invalid-registration`; no crea cuenta ni cliente, y pedidos y stock permanecen sin cambios

#### Scenario: Contraseña no representable sin truncamiento
- **WHEN** se registra o cambia una contraseña que supera el límite de entrada del codificador de contraseñas
- **THEN** responde HTTP 400 con type `https://api.master-acdc.dev/problems/invalid-password`, sin truncar la contraseña ni modificar cuentas, clientes, familias, pedidos o stock

### Requirement: Login con errores indistinguibles
El sistema SHALL aceptar `POST /api/auth/login` con email y password, normalizando el email igual que en registro. Con credenciales válidas de cuenta habilitada SHALL responder 200 `{accessToken,expiresIn}` con expiresIn 900 y nueva familia de refresh. Email inexistente y contraseña incorrecta SHALL producir el mismo 401, cuerpo y un coste comparable de verificación de contraseña.

#### Scenario: Registro seguido de login y llamada autenticada
- **WHEN** una cuenta recién registrada inicia sesión y usa su bearer en `GET /api/orders?status=PAID`
- **THEN** login responde HTTP 200 con expiresIn 900 y accessToken, y la llamada autenticada responde HTTP 200

#### Scenario: Login no revela existencia del email
- **WHEN** se comparan logins con email inexistente y con contraseña incorrecta para un email existente
- **THEN** ambos responden HTTP 401 con cuerpo idéntico y type `https://api.master-acdc.dev/problems/invalid-credentials`, con verificación de contraseña de coste comparable
- **AND** no se crea familia ni se cambian cuentas, pedidos o stock

#### Scenario: Cuenta deshabilitada no inicia sesión
- **WHEN** se intenta login con credenciales de una cuenta deshabilitada
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/invalid-credentials`, sin emitir tokens ni modificar pedidos o stock

### Requirement: Cookies de renovación y protección del secreto
El sistema SHALL emitir refresh_token solo en cookie HttpOnly, Secure, SameSite=Strict, Path=/api/auth en login y refresh. El refresh SHALL ser impredecible y el almacenamiento persistente SHALL contener solo su hash SHA-256. Login SHALL emitir además XSRF-TOKEN legible por JavaScript, Secure, SameSite=Strict, Path=/.

#### Scenario: Login emite cookies utilizables y seguras
- **WHEN** se realiza un login válido sin cookie CSRF previa
- **THEN** responde HTTP 200 y emite ambas cookies con los atributos definidos; el refresh no aparece en el cuerpo ni en claro en el almacenamiento persistente

### Requirement: Access tokens verificables y mínimos
El sistema SHALL emitir JWT RS256 con vida de 900 segundos, header kid igual al thumbprint RFC 7638 de la pública, y exactamente los claims iss, sub, aud, iat, exp, jti y roles. sub SHALL ser el UUID de la cuenta y jti único. SHALL validar firma RS256, selección por kid, vigencia, issuer y audience configurados, sin normalizar sus barras finales.

#### Scenario: Claims exactos del access token
- **WHEN** se obtiene un access token por login o refresh válido
- **THEN** responde HTTP 200 y el JWT contiene exactamente los siete claims, exp menos iat es 900, roles contiene CUSTOMER y sub es el id del registro; no hay email, permisos, customerId ni sv

#### Scenario: Token inválido no autentica
- **WHEN** se llama `GET /api/orders?status=PAID` con un JWT con otra aud, otro iss (incluida barra final distinta), caducado, firmado por otra clave, algoritmo distinto o kid desconocido
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/unauthorized` y challenge Bearer; pedidos y stock permanecen sin cambios

### Requirement: Publicación de clave pública
El sistema SHALL publicar `GET /.well-known/jwks.json` sin autenticación con únicamente la parte pública de la clave RSA 2048 y su kid. La clave privada SHALL proceder de un PEM externo cuya ruta configura shop.jwt.private-key, alimentada en producción por SHOP_JWT_PRIVATE_KEY, nunca del repositorio ni de recursos empaquetados.

#### Scenario: JWKS público verifica la firma
- **WHEN** se consulta JWKS anónimamente y se verifica un access token obtenido por login
- **THEN** JWKS responde HTTP 200, su kid coincide con el header del token, la firma verifica y no se publican parámetros privados

### Requirement: Rotación de refresh y doble vencimiento
El sistema SHALL aceptar `POST /api/auth/refresh` con cookie y protección CSRF válida, consumir el refresh vigente una sola vez y responder 200 con nuevo accessToken, expiresIn 900 y nueva cookie refresh_token. Cada refresh SHALL vencer a los 7 días de emisión o al cumplirse 30 días desde login, lo que ocurra antes. La rotación SHALL conservar el límite absoluto de familia.

#### Scenario: Refresh válido emite nuevo par
- **WHEN** se presenta un refresh vigente con cabecera CSRF válida
- **THEN** responde HTTP 200 con access token y refresh diferentes de los anteriores y conserva la familia y su límite absoluto

#### Scenario: Refresh vencido o desconocido
- **WHEN** se presenta con CSRF válido un refresh desconocido, revocado, sin cookie o que alcanzó su vencimiento individual o familiar
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/invalid-refresh-token`; no emite sucesor ni modifica pedidos o stock

#### Scenario: Rotación no prolonga la familia
- **WHEN** se renueva una familia en su día 29 con refresh aún vigente y CSRF válido
- **THEN** responde HTTP 200 y el refresh nuevo vence en el día 30 original; intentar renovarlo entonces responde HTTP 401 con type `https://api.master-acdc.dev/problems/invalid-refresh-token`, sin modificar pedidos o stock

### Requirement: Reutilización revoca la familia de forma atómica
El sistema SHALL detectar la presentación de un refresh ya rotado, responder 401 y confirmar la revocación de toda su familia, incluido cualquier sucesor. Con dos refresh simultáneos del mismo token SHALL haber exactamente una rotación exitosa y un rechazo por reutilización; el sucesor de la ganadora SHALL quedar revocado tras el rechazo.

#### Scenario: Reutilización invalida todos los sucesores
- **WHEN** se rota un refresh y se presenta otra vez el anterior con CSRF válido
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/invalid-refresh-token` y revoca su familia; usar el sucesor responde HTTP 401 con el mismo type
- **AND** otras familias, pedidos y stock permanecen sin cambios

#### Scenario: Dos refresh simultáneos tienen un solo ganador
- **WHEN** dos peticiones simultáneas presentan el mismo refresh vigente con CSRF válido
- **THEN** exactamente una responde HTTP 200 y la otra HTTP 401 con type `https://api.master-acdc.dev/problems/invalid-refresh-token`; tras ambas, la familia está revocada y no se han modificado pedidos ni stock

### Requirement: Contención de identidad con espera acotada
El sistema SHALL limitar a dos segundos la espera por bloqueo al modificar credenciales o familias. Si no puede obtenerlo, SHALL responder 503 con Retry-After: 1 y revertir esa operación, sin eliminar la protección de concurrencia.

#### Scenario: Familia ocupada no produce rotación parcial
- **WHEN** un refresh válido con CSRF correcto no puede obtener el bloqueo durante dos segundos
- **THEN** responde HTTP 503 con type `https://api.master-acdc.dev/problems/authentication-busy` y Retry-After: 1; no rota ni revoca tokens y no modifica pedidos ni stock

### Requirement: Logout revoca la familia presentada
El sistema SHALL aceptar `POST /api/auth/logout` con cookie refresh_token conocida y CSRF válido, revocar su familia y responder 204 borrando la cookie con los mismos atributos y Path. Una familia ya revocada SHALL permitir repetir logout con 204; cookie ausente o desconocida SHALL producir 401.

#### Scenario: Logout impide renovar esa familia
- **WHEN** se hace logout de una familia conocida con CSRF válido
- **THEN** responde HTTP 204, borra la cookie y una renovación posterior responde HTTP 401 con type `https://api.master-acdc.dev/problems/invalid-refresh-token`; otras familias, pedidos y stock permanecen sin cambios

#### Scenario: Logout sin refresh conocido
- **WHEN** se hace logout con CSRF válido y cookie ausente o desconocida
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/invalid-refresh-token`, sin modificar familias, pedidos ni stock

### Requirement: Cambio de contraseña revoca todas las familias
El sistema SHALL aceptar `POST /api/users/me/password` autenticado con `{currentPassword,newPassword}`, comprobar la contraseña actual y aplicar la política de nueva contraseña. SHALL cambiar el hash y revocar todas las familias del usuario atómicamente, respondiendo 204.

#### Scenario: Cambio de contraseña cierra renovación de otro dispositivo
- **WHEN** un usuario con dos familias cambia correctamente su contraseña
- **THEN** responde HTTP 204 y refresh desde cualquiera de ambos dispositivos responde HTTP 401 con type `https://api.master-acdc.dev/problems/invalid-refresh-token`
- **AND** las familias de otros usuarios, los pedidos y el stock permanecen sin cambios

#### Scenario: Contraseña actual incorrecta
- **WHEN** un usuario autenticado intenta cambiar contraseña con currentPassword incorrecta
- **THEN** responde HTTP 401 con type `https://api.master-acdc.dev/problems/invalid-credentials`, sin cambiar contraseña, familias, pedidos ni stock
