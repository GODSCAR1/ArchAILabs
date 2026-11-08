# Tasks

Solo planificación: todas las casillas quedan pendientes. Aplicación tras revisión y una nueva solicitud explícita. Las tareas **HUMANA** no las ejecuta el agente. La extensión global de test aporta claves y propiedades, no sustituye las adaptaciones humanas de autenticación HTTP. Si un guardián bloquea un paso, detenerse.

Cada test de escenario usa `@DisplayName("<nombre exacto del escenario>")` del delta correspondiente, y nombre Java `should_<comportamiento>_when_<condición>`. Los tests mencionados son verificaciones previstas, no pruebas ya implementadas.

## 1. Preparación y decisiones protegidas

- [ ] 1.1 **HUMANA** revisar propuesta, conservar/revisar el diff existente de pom.xml con Security/Resource Server/tests y autorizar cualquier ajuste de dependencias necesario. Verificación: compilación de tests y resolución de `jwt()` de spring-security-test, sin editar dependencias por iniciativa del agente.
- [ ] 1.2 **HUMANA** incorporar el siguiente bloque en `src/main/resources/application.properties`, proporcionar PEM RSA externo y variables SHOP_JWT_PRIVATE_KEY/SHOP_OPS_USER/SHOP_OPS_PASSWORD_HASH. Issuer y audience están decididos; conservar las dos líneas existentes de Actuator sin duplicarlas ni cambiar su exposición. Verificación: JwtConfigurationTest para valores exactos y configuración ausente/inválida, y ActuatorSecurityIT; ningún PEM ni secreto versionado.

  ```properties
  shop.jwt.private-key=${SHOP_JWT_PRIVATE_KEY}
  shop.jwt.issuer=https://api.master-acdc.dev
  shop.jwt.audience=master-acdc-api
  shop.ops.user=${SHOP_OPS_USER}
  shop.ops.password-hash=${SHOP_OPS_PASSWORD_HASH}

  management.endpoints.web.exposure.include=health,metrics
  management.endpoint.health.show-details=never
  ```

- [ ] 1.3 Leer package-info de los paquetes que se amplían y crear estructura/package-info de identity; añadir IdentityArchitectureTest con las dependencias descritas en design. Verificación: IdentityArchitectureTest y los guardianes ArchitectureTest, CatalogArchitectureTest, CatalogMongoArchitectureTest, PersistenceRulesTest y DomainSignatureRulesTest intactos.

## 2. Persistencia y registro atómico

- [ ] 2.1 Crear V8 con cuentas, FK cliente única nullable, hashes de refresh, índices family_id/user_id y family_expires_at. Verificación: IdentityPersistenceIT en PostgreSQL real comprueba UNIQUE normalizado, FK, session_version=0, null permitido para no clientes e índices.
- [ ] 2.2 Implementar modelos/puertos y adaptadores de persistencia con mappers dedicados, UUID escalares o asociaciones LAZY, y consultas parametrizadas. Verificación: UserAccountPersistenceMapperTest, RefreshTokenPersistenceMapperTest, IdentityPersistenceIT y PersistenceRulesTest.
- [ ] 2.3 Añadir frontera pública CustomerRegistrationFacade en ordering e implementación con su persistencia existente, más adaptador de identity; registro con TransactionTemplate común y traducción de duplicado fuera del rollback. Verificación: RegisterServiceTest, CustomerRegistrationFacadeIT y IdentityPersistenceIT con fallo forzado tras crear cliente y registro duplicado concurrente; ningún cliente huérfano.
- [ ] 2.4 Implementar política de email/name/password, recurso common-passwords.txt y encoder delegado {bcrypt}; hash de relleno calculado una vez. Verificación: PasswordPolicyTest, PasswordEncoderAdapterTest y RegisterServiceTest cubren 12 caracteres, común, límite del encoder sin truncamiento, normalización y nombre opcional; sin lista hardcodeada.
- [ ] 2.5 Exponer register y mapper web: 201 solo {id}, sin cabecera Location ni tokens/customerId; rechazos 400/409. Verificación: IdentityWebMapperTest y AuthFlowIT con escenarios «Registro con nombre explícito», «Registro sin nombre», «Registro duplicado no crea cliente huérfano», «Contraseña corta o común», «Datos de registro inválidos» y «Contraseña no representable sin truncamiento»; comprobar ausencia de Location.

## 3. Claves y emisión de tokens

- [ ] 3.1 Crear GlobalIdentityTestExtension autodetectada mediante META-INF/services siguiendo GlobalMongoTestContainerExtension: generar una sola RSA 2048 por JVM, PEM privado temporal fuera del repo y propiedades de sistema shop.jwt.private-key, shop.jwt.issuer, shop.jwt.audience, shop.ops.user y shop.ops.password-hash antes de cargar contextos. Mantener el PEM hasta finalizar la JVM y limpiarlo después. Conservar fixture PostgreSQL/Redis para nuevos IT y Mongo compartido cuando sea necesario. Verificación: GlobalIdentityTestExtensionTest y JwtConfigurationTest prueban autodetección, reutilización entre contextos SpringApplicationBuilder y carga del mismo PEM por el cargador de producción; AuthFlowIT arranca sin imports de configuración efímera.
- [ ] 3.2 Implementar carga PEM desde la propiedad canónica shop.jwt.private-key (SHOP_JWT_PRIVATE_KEY desde entorno, enlazada en 1.2), RSA 2048, kid RFC 7638, JWKSource único para encoder y decoder; añadir *.pem a .gitignore. Verificación: JwtConfigurationTest comprueba origen compartido, rechazo de clave inválida y selección de kid; `git check-ignore` sobre una ruta PEM de ejemplo y revisión de secretos.
- [ ] 3.3 Implementar puerto emisor, claims exactos y validadores RS256/iss/aud/tiempo/kid; publicar solo JWK pública. Verificación: JwtTokenAdapterTest y AuthFlowIT con «Claims exactos del access token», «Token inválido no autentica», «JWKS público verifica la firma», incluyendo issuer con barra distinta y JWT firmado correctamente con kid equivocado.
- [ ] 3.4 Implementar LoginUseCase/Service, nueva familia y respuesta/cookies; comparar contra hash de relleno para usuario desconocido. Verificación: LoginServiceTest y PasswordEncoderAdapterTest (una generación de relleno, matches en ambos fallos); AuthFlowIT con «Registro seguido de login y llamada autenticada», «Login no revela existencia del email», «Cuenta deshabilitada no inicia sesión» y «Login emite cookies utilizables y seguras».

## 4. Familias, revocación y contraseña

- [ ] 4.1 Implementar generación opaca y hash SHA-256, doble caducidad y rotación con lock de cuenta antes de SELECT FOR UPDATE por hash; mantener familia y tope absoluto. Verificación: RefreshServiceTest con Clock fijo, IdentityPersistenceIT y AuthFlowIT con «Refresh válido emite nuevo par», «Refresh vencido o desconocido» y «Rotación no prolonga la familia»; assert de hash y ausencia de secreto en BD.
- [ ] 4.2 Implementar replay como resultado rechazado con revocación confirmada antes del 401, sin rollback accidental. Verificación: RefreshServiceTest y AuthFlowIT con «Reutilización invalida todos los sucesores», usando el sucesor después del error y comprobando que otra familia permanece activa.
- [ ] 4.3 Cubrir concurrencia y espera acotada sin mocks de BD: dos peticiones independientes con barrera y locks/hints reales. Verificación: RefreshTokenConcurrencyIT con «Dos refresh simultáneos tienen un solo ganador» (exactamente 200/401, sucesor revocado) y RefreshTokenLockTimeoutIT con «Familia ocupada no produce rotación parcial» (503, Retry-After y rollback).
- [ ] 4.4 Implementar logout de familia y borrado de cookie, incluyendo repetición idempotente y desconocido. Verificación: LogoutServiceTest y AuthFlowIT con «Logout impide renovar esa familia», «Logout sin refresh conocido» y repetición 204; se mantienen familias de otros dispositivos.
- [ ] 4.5 Implementar cambio de contraseña con verificación actual y revocación masiva en la misma transacción; serializarlo con login/refresh para no crear sucesor con credenciales antiguas después del commit. Verificación: ChangePasswordServiceTest, IdentityPersistenceIT con carrera password/refresh y AuthFlowIT con «Cambio de contraseña cierra renovación de otro dispositivo», «Contraseña actual incorrecta» y política de contraseña nueva. Verificar rollback ante rechazo; la vigencia residual de access tokens queda como límite conocido en design, no como aserción de este escenario.

## 5. Cadenas HTTP y contratos de error

- [ ] 5.1 Configurar cadenas ordenadas Actuator, JWKS y API stateless con permitAll por método y rutas exactas; este change define autenticación y add-rbac-and-object-authorization define autorización. Verificación: ApiSecurityTest con «Lectura pública del catálogo», «Reserva bajo products no es pública» y «Sesión HTTP no sustituye bearer»; comprobar GET categories en matcher y que POST no hereda apertura.
- [ ] 5.2 Implementar CSRF solo refresh/logout con XSRF-TOKEN legible por JS, Secure, SameSite=Strict y Path=/, handler crudo y carga efectiva del token diferido al login; refresh_token conserva HttpOnly y Path=/api/auth. Verificación: AuthFlowIT/ApiSecurityTest con «Refresh sin cabecera CSRF», «Logout con CSRF incorrecto», «Cabecera CSRF acepta valor crudo» y «Registro y login no requieren CSRF»; comprobar atributos y accesibilidad de XSRF desde la ruta de SPA con cookies/cabecera reales, no solo csrf().
- [ ] 5.3 Implementar EntryPoint/DeniedHandler y errores de dominio con ProblemDetail y challenges preservados. Verificación: ApiSecurityTest con «Error bearer conserva challenge y formato» y «Error CSRF conserva formato de problema»; AuthFlowIT verifica type/cuerpo de todos los rechazos, sin PII ni secretos.
- [ ] 5.4 Aislar Basic OPS de bearer API con EndpointRequest y @Order(1). Verificación: ActuatorSecurityIT con «Health público», «Bearer ADMIN no autentica operaciones», «Operador accede con Basic» y «Basic de operaciones no autentica API»; usar JWT real para el rechazo de ADMIN y comprobar challenge Basic.

## 6. Adaptación coordinada de tests existentes

- [ ] 6.1 Adaptar con jwt() los tests NO protegidos ProductControllerTest, CreateProductIT, ProductSearchMongoIT, OrderPlacementIT, OrderCancellationIT, OrderControllerTest, OrderListingIT y StockReservationIT según tabla de design; las claves y propiedades llegan por la extensión global. Verificación: esos mismos tests conservan expectativas de negocio sin desactivar filtros.
- [ ] 6.2 Importar la cadena real y sus dependencias necesarias en los slices @WebMvcTest ProductControllerTest, ProductDetailControllerTest y OrderControllerTest, manteniendo GET de catálogo anónimos. Verificación: esos tres tests con filtros activos y propiedades de la extensión global; no introducir imports de claves efímeras en contextos completos.
- [ ] 6.3 **HUMANA** adaptar CatalogProductEditingMongoIT y CatalogProductCacheEditingIT: jwt() en preparación POST y operaciones PUT, mismos asserts de versiones/cache. Verificación: ambos guardianes originales en verde y diff revisado por la persona; el agente no edita estos archivos.
- [ ] 6.4 **HUMANA** adaptar StockReservationConcurrencyTest con bearer real firmado mediante encoder del contexto o el mismo PEM temporal global para HttpClient, preservando 50 reservas, 30/20 y timeout. Verificación: el guardián original en verde con los mismos umbrales; no sustituirlo por MockMvc ni cambiar el stock inicial.
- [ ] 6.6 Añadir cobertura de las nuevas precondiciones sin editar guardianes: ApiSecurityTest y AuthFlowIT trazan «Pedido anónimo se rechaza antes de validar cabecera», «Pedido autenticado conserva validación de cabecera», «Listado y cancelación requieren JWT», «Reserva anónima no llega al inventario», «Reserva con JWT conserva contrato de éxito», «Escritura anónima no modifica catálogo» y «Edición autenticada conserva conflicto de versión». Verificación: esos tests prueban estado de pedidos/stock sin cambios en rechazos y conservan X-Customer-Id.

## 7. Integración y entrega

- [ ] 7.1 Completar evidencia de integración de todo el flujo con los tests anteriores, cada @DisplayName exacto, y documentar en docs/lab-s12.md las decisiones de este recorte, cookies/CSRF, variables externas, vigencia residual del JWT y tareas humanas realizadas. Verificación: AuthFlowIT, RefreshTokenConcurrencyIT, RefreshTokenLockTimeoutIT y ActuatorSecurityIT; contraste documentación/resultados sin declarar cumplidos pasos 5–9.
- [ ] 7.2 Ejecutar `bash ./mvnw spotless:apply`, `graphify update .` después de implementar código, `openspec validate add-authentication --strict --no-interactive`, `bash ./verify-quality.sh --fast` y `bash ./mvnw -q verify`; pegar salida completa de Verify. Verificación: tests unitarios, ArchUnit y todos los IT en verde, guardianes intactos salvo cambios humanos revisados, sin bajar PIT 75%. Si se necesita cambiar el loop protegido, detenerse y pedir permiso; fast no acredita PIT ni Gitleaks.

## Workflow follow-up

- Una persona revisa la implementación y gestiona sincronización/archive; este workflow no los ejecuta.
- Continuar RBAC/propiedad y versión de sesión en sus changes independientes, manteniendo aquí claims exactos sin sv y cabecera de pedidos vigente.
