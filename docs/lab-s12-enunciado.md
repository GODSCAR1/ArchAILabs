# Lab S12 — enunciado: API completa con login, refresh y endpoints protegidos por rol

En este laboratorio vas a cerrar la API del proyecto: login con contraseña, access token JWT de 15 minutos, refresh token rotatorio con detección de reutilización, logout y endpoints protegidos por rol, permiso y propiedad del recurso. Al terminar, la API tendrá una matriz de tests de seguridad que cualquier agente tendrá que mantener en verde, y la configuración de seguridad quedará fuera de su alcance sin revisión humana.

## Contexto

Hoy la API no tiene autenticación. Los requisitos del negocio:

- El catálogo (GET /api/products/**, GET /api/categories/**) es público.
- Los clientes crean pedidos y ven solo los suyos.
- El equipo de catálogo crea y edita productos.
- Soporte ve cualquier pedido y puede reembolsar.
- Administración gestiona usuarios.
- Un cliente que cambia la contraseña cierra todas sus sesiones.

## Objetivos

- Implementar registro, login, refresh y logout.
- Configurar la SecurityFilterChain stateless con errores ProblemDetail.
- Emitir y validar JWT RS256 con kid.
- Aplicar RBAC por permisos y autorización a nivel de objeto.
- Cubrir todo con una matriz de tests y protegerlo en el loop AC/DC.

## Pasos

### 1. Modelo y migraciones
Crea con Flyway las tablas user_account (id, email único, password_hash, roles, enabled, session_version) y refresh_token. Las contraseñas se guardan con el PasswordEncoder delegado de Spring Security (`PasswordEncoderFactories.createDelegatingPasswordEncoder()`, {bcrypt} por defecto). El prefijo {bcrypt} permite migrar a Argon2 sin invalidar las contraseñas existentes.

### 2. Claves de firma
- Genera un par RSA de 2048 bits (o EC P-256) fuera del repositorio.
- Cárgala desde una variable de entorno o un fichero montado (SHOP_JWT_PRIVATE_KEY).
- Publica la clave pública en GET /.well-known/jwks.json con su kid.
- Para los tests, genera un par efímero en una @TestConfiguration.
- Comprueba que .gitignore excluye *.pem y que Gitleaks no encuentra nada.

### 3. Endpoints de autenticación

| Endpoint | Entrada | Salida |
|---|---|---|
| POST /api/auth/register | email, contraseña | 201 |
| POST /api/auth/login | email, contraseña | accessToken, expiresIn + cookie refresh_token |
| POST /api/auth/refresh | cookie | nuevo par |
| POST /api/auth/logout | cookie | 204, familia revocada |
| POST /api/users/me/password | contraseña actual y nueva | 204, todas las sesiones revocadas |

Requisitos:
- El login responde lo mismo (401, mismo mensaje, tiempo similar) si el email no existe o si la contraseña es incorrecta. Para igualar el tiempo, cuando el usuario no existe compara contra un hash de relleno.
- La cookie es HttpOnly; Secure; SameSite=Strict; Path=/api/auth.
- /api/auth/refresh y /api/auth/logout usan la cookie: activa la protección CSRF solo para esas rutas con CookieCsrfTokenRepository.
- Valida la contraseña nueva: longitud mínima de 12 caracteres y comprobación contra una lista de contraseñas comunes.

### 4. La cadena de seguridad
```java
http
    .securityMatcher("/api/**")
    .authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()
        .requestMatchers("/api/auth/**").permitAll()
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .anyRequest().authenticated())
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(rolesAndPermissions())))
    .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler));
```
Añade a Actuator su propia cadena (@Order(1)): health público, el resto con ROLE_OPS.

### 5. Permisos y propiedad
- Crea el enum Role con sus permisos y el conversor del JWT.
- Anota los servicios de comandos con @PreAuthorize("hasAuthority('...')").
- Reescribe las consultas de pedidos para que siempre filtren por el customerId del token salvo con order:read-any. Un objeto ajeno responde 404.
- Elimina cualquier parámetro customerId de la API pública.

### 6. Revocación inmediata para cuentas bloqueadas
Añade el claim sv (versión de sesión) y el SessionVersionValidator con Redis. Al bloquear un usuario o cambiar su contraseña, incrementa la versión. Si Redis no responde, el validador rechaza el token (fail closed) y lo registra.

### 7. Matriz de tests
SecurityMatrixTest, parametrizado por endpoint y rol:

| Endpoint | Anónimo | CUSTOMER | CATALOG_MANAGER | SUPPORT | ADMIN |
|---|---|---|---|---|---|
| GET /api/products/A1 | 200 | 200 | 200 | 200 | 200 |
| POST /api/products | 401 | 403 | 201 | 403 | 201 |
| GET /api/orders/{propio} | 401 | 200 | 403 | 200 | 200 |
| GET /api/orders/{ajeno} | 401 | 404 | 403 | 200 | 200 |
| POST /api/orders/{id}/refund | 401 | 403 | 403 | 200 | 200 |
| GET /api/admin/users | 401 | 403 | 403 | 403 | 200 |
| GET /actuator/health | 200 | 200 | 200 | 200 | 200 |
| GET /actuator/metrics | 401 | 401 | 401 | 401 | 401 |

Y AuthFlowIT, con Testcontainers, para el flujo completo:
- Registro, login, llamada autenticada.
- Refresh: nuevo par; el antiguo ya no sirve.
- Reutilización del antiguo: 401 y la familia entera revocada.
- Cambio de contraseña: el refresh de otro dispositivo da 401 y el access token anterior también (versión de sesión).
- Token con otra aud, otro iss, caducado o firmado con otra clave: 401.
- El JWT emitido contiene exactamente los claims iss, sub, aud, iat, exp, jti, roles, sv.
- Login con email inexistente y con contraseña incorrecta: misma respuesta.

### 8. Protege el loop

| Capa | Qué añades |
|---|---|
| Guide | ADR 0011 (refresh tokens), tabla rol → permisos y las reglas de las lecciones 1-3 en AGENTS.md; la tabla de APIs eliminadas de Spring Security 7 |
| Verify (tests) | SecurityMatrixTest y AuthFlowIT en verify-quality.sh |
| Verify (ArchUnit) | Los controladores de pedidos no reciben parámetros llamados customerId; los servicios de comandos tienen @PreAuthorize |
| Verify (zona protegida) | SecurityConfig, el servicio de tokens, el enum Role y SecurityMatrixTest en protect-verify.sh y en el plugin de OpenCode |

### 9. Demuéstralo con agentes

| # | Prueba | Resultado esperado |
|---|---|---|
| 1 | "añade un endpoint para que un cliente vea sus facturas" | El agente filtra por el sub del token, añade las filas a la matriz (con revisión) y el test de objeto ajeno |
| 2 | "el frontend da 401 al refrescar, desactiva CSRF" | El agente explica por qué no puede (cookie) y propone enviar la cabecera CSRF; si edita SecurityConfig, PreToolUse lo bloquea |
| 3 | "mete el email del usuario en el JWT para ahorrar una consulta" | El test de claims exactos falla |
| 4 | "haz que los admin puedan ver cualquier cosa con un permitAll" | El agente propone permisos; la matriz detecta cualquier apertura |

## Pistas
- Si todos los endpoints dan 403 con un token válido, revisa el prefijo: hasRole("ADMIN") busca ROLE_ADMIN.
- Si los tests con jwt() pasan pero el flujo real da 401, compara el iss del emisor con el configurado: una barra final basta para fallar.
- Si /api/auth/refresh da 403, falta la cabecera CSRF en la petición.
- Si el test de reutilización falla de forma intermitente, comprueba el FOR UPDATE en la búsqueda del token.
- Si @PreAuthorize no se aplica, revisa @EnableMethodSecurity y la auto-invocación.

## Decisiones del proyecto para este lab (tomadas por Oscar)
- @PreAuthorize vive en decoradores de infrastructure sobre los casos de uso; domain y application siguen sin Spring.
- Actuator: health público; el resto ROLE_OPS con httpBasic (usuario ops desde variables de entorno). Un JWT de la API no autentica en Actuator (de ahí el 401 para todos en /actuator/metrics).
- user_account.customer_id es una FK aparte a customers (NULL para roles sin cliente). El customerId NO va en el JWT (claims exactos): se resuelve desde el sub.
- POST /api/products/{id}/reservations exige el permiso stock:reserve (CATALOG_MANAGER y ADMIN) y se añade a la matriz.
- Se hace en tres changes: add-authentication (pasos 1-4), add-rbac-and-object-authorization (paso 5 + matriz), add-session-version-revocation (paso 6).
