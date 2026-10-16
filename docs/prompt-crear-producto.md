Contexto: proyecto master-acdc (Spring Boot 4.1, Java 25, hexagonal; sigue AGENTS.md y lee el package-info de cada paquete antes de añadir clases). Las decisiones de esta tarea están en docs/decisiones/crear-producto.md: léelo entero antes de empezar. Las dependencias (JPA, validación, Flyway, PostgreSQL, Testcontainers) y failsafe ya están en el pom.

Objetivo: poder crear productos con POST /api/products.

Restricciones:
- Solo crear. No implementes GET, listados ni otros endpoints.
- No toques pom.xml ni verify-quality.sh. Si te falta una dependencia o un cambio ahí, detente y pídemelo.
- Tests unitarios: *Test. Tests con Testcontainers: *IT.
- La carrera de SKU duplicado se resuelve con la restricción UNIQUE de la migración + traducción en el adaptador de persistencia. Nada de locks ni synchronized.
- Si necesitas tomar una decisión que no está en el documento de decisiones ni en estos criterios, pregunta antes de suponer.

Criterios de aceptación (precondición → resultado). En todos los 400 y 409: ProblemDetail (application/problem+json) y no se guarda nada. Ningún caso devuelve 500.
1. {"name":"Cafe Huila 500 g","price":32000,"sku":" caf-500 "} → 201; Location /api/products/{uuid}; cuerpo {id, name, price, sku} con sku "CAF-500"; existe una fila con ese sku.
2. sku "AB" (2) → 400. sku "ABC" (3) → 201.
3. sku de 64 caracteres → 201. De 65 → 400.
4. sku "CAF_500" → 400.
5. Ya existe "CAF-500"; llega "caf-500" → 409; sigue habiendo una sola fila con ese sku.
6. Carrera (IT del adaptador de persistencia): guardar dos productos con el mismo sku directamente en el adaptador, sin la comprobación previa → el segundo lanza la excepción de dominio de duplicado, no DataIntegrityViolationException.
7. name de 120 caracteres → 201. De 121 → 400. "   " → 400. "  Cafe  " se guarda como "Cafe".
8. price 1 → 201. 0 → 400. -5 → 400. 1500.5 → 400 (no se trunca a 1500). Sin price → 400.

Verificación:
- Primero escribe los tests de estos criterios, cada uno nombrado como su criterio (should_<comportamiento>_when_<condición>), ejecútalos y pega la salida en rojo antes de implementar.
- Al terminar ejecuta `bash ./verify-quality.sh --fast` y `bash ./verify-quality.sh --full` (Docker está arrancado) y pega ambas salidas completas.
- Después ejecuta `graphify update .`.
