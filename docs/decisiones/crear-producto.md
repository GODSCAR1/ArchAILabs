# Decisiones: crear producto (lab S03, paso 4)

Salida de la sesión de `grilling` (3 rondas, 19 preguntas) más la revisión A–H de la persona.
Este archivo es la fuente de las decisiones; el prompt del paso 5 se escribe a partir de él.

## Alcance
- Solo **crear** un producto: `POST /api/products`. Nada de consultar, listar, editar ni borrar.
- Sin autenticación (fuera de alcance del lab). Credenciales de BD solo por variables de entorno.

## Modelo
| Campo | Regla | Normalización |
|---|---|---|
| `id` | `UUID` generado por la aplicación (no por la BD) | — |
| `name` | 1–120 caracteres tras normalizar; vacío o solo espacios → 400 | `trim` |
| `sku` | `^[A-Z0-9-]{3,64}$` tras normalizar; único | `trim` + mayúsculas; la unicidad se evalúa sobre el valor normalizado |
| `price` | Pesos colombianos (COP), entero, **> 0**; decimales → 400 | — |

- Sin `createdAt` ni otros campos de auditoría.

## Contrato HTTP
- Éxito: `201 Created` + cabecera `Location: /api/products/{id}` + cuerpo `{id, name, price, sku}` (con `sku` normalizado).
  - Nota: aún no existe `GET /api/products/{id}`; `Location` apunta a un recurso que se implementará después.
- Validación: `400` con `ProblemDetail`.
- SKU duplicado: `409` con `ProblemDetail`, **también en carrera**: si dos peticiones pasan la comprobación previa, la restricción única de la BD lanza `DataIntegrityViolationException` y el adaptador de persistencia la traduce a la excepción de dominio que acaba en 409 (nunca 500).
- Código, nombres y JSON en inglés.

## Validación (en los dos lados)
- DTO de entrada (`@Valid`): presencia y tipo (campos obligatorios, `price` numérico).
- Value objects de dominio (`record` que validan en el constructor): normalización y reglas de formato/rango. Son la única fuente de los límites (3–64, 1–120, > 0) para no duplicarlos con distinta normalización.

## Arquitectura
- Puerto de entrada: interfaz `application/port/CreateProductUseCase` + implementación `application/CreateProductService`.
- Puerto de salida: `domain/port/ProductRepository` (solo interfaz).
- Adaptadores: `infrastructure/adapters/in/web` (controller, DTOs, manejo de errores) y `infrastructure/adapters/out/persistence` (entidad JPA, repositorio Spring Data, adaptador).
- Ensamblado de beans en `infrastructure/config/` con `@Bean`.

## Persistencia
- PostgreSQL + Spring Data JPA + Flyway. Migración `V1__create_products.sql` con restricción `UNIQUE` sobre `sku`.
- `spring.jpa.hibernate.ddl-auto=validate` (el esquema lo manda Flyway).

## Tests y Verify
- Unitarios (dominio, caso de uso, `@WebMvcTest`): `*Test`, corren en `verify-quality.sh --fast`.
- Integración con Testcontainers (PostgreSQL real): `*IT`, corren **solo** en `verify-quality.sh --full` (failsafe). Necesitan Docker.
- `MasterAcdcApplicationTests` (`@SpringBootTest`) necesitará una BD al añadir JPA: pasa a ser `*IT` con Testcontainers.

## Cambios en zona protegida (los hace la persona, no el agente)
- `pom.xml`: dependencias de JPA, validación, Flyway, PostgreSQL, Testcontainers y plugin failsafe.
- `verify-quality.sh`: `--fast` = `mvn test`; `--full` = `mvn verify`.

## Decisiones aceptadas sin discutir (para el paso 7)
De 19 recomendaciones de `grilling`, la mayoría se aprobaron sin cambio. La revisión posterior cambió dos: moneda (EUR → COP, decimales fuera) y tests de integración (de siempre a solo `--full`).
