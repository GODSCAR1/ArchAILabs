# Design

## Context

V4 define `customers.name VARCHAR(120) NOT NULL`, sin prohibir cadenas vacías. `Registration` rechaza `isBlank()` y nombres de más de 120 caracteres; cuando falta el nombre usa la parte local del email. `CustomerRegistrationAdapter` repite esa validación y el mapper crea `CustomerJpaEntity` con el nombre original. Las consultas de pedidos siguen leyendo `name`. Los IT de migración crean bases aisladas en el servidor PostgreSQL compartido, sin añadir contenedores.

## Goals / Non-Goals

**Goals:** compatibilidad durante expandir, equivalencia SQL/Java verificable, rollback transaccional y validación real del esquema JPA.

**Non-Goals:** no cambiar la validación pública, corregir automáticamente datos históricos ni implementar contraer.

## Decisions

1. **V11 aditiva y transaccional.** Añadir `first_name` y `last_name` como `VARCHAR(120)` anulables, rellenar desde `name` sin modificarlo y no imponer `NOT NULL` en esta fase: los despliegues antiguos y los fixtures que solo escriben `name` deben seguir funcionando durante la coexistencia. Los checks hacen visibles sus posibles incumplimientos. Antes de alterar datos, contar `name = ''` o nombres compuestos únicamente por espacios U+0020; si hay alguno, lanzar un error SQL que incluya el recuento y pida corrección humana, sin exponer nombres. Flyway ejecuta V11 en una transacción PostgreSQL; comprobar que el error revierte tanto DDL como datos y no deja V11 aplicada. Alternativa descartada: valores de sustitución o alterar V4.
2. **Regla idéntica y explícita.** Normalizar solo el carácter espacio U+0020 en extremos y secuencias internas; otros caracteres, incluidas tabulaciones, se conservan. Partir en el primer espacio normalizado; el resto completo es `last_name`, o NULL si no hay separador. Esta precisión evita diferencias entre `trim`, regex Java y PostgreSQL y no amplía la validación existente; los tests incluyen caracteres fuera de U+0020 y longitudes límite. No se cambia `name` ni se truncan componentes.
3. **Dominio único para nuevas escrituras.** Un value object puro de ordering encapsula la partición en un solo lugar; `CustomerRegistrationAdapter` lo utiliza y `CustomerRegistrationPersistenceMapper` traslada sus valores a `CustomerJpaEntity`. Identity sigue llamando a `CustomerRegistrationFacade` con un único nombre. La validación existente se conserva. Alternativa descartada: duplicar la partición entre adaptador y mapper o repartirla entre módulos.
4. **Checks ejecutables y detectables.** En `src/main/resources/db/checks/`, una consulta SELECT por fichero devuelve las filas infractoras. Cubrir primer nombre no nulo/no vacío, reconstrucción con `concat_ws`, recorte de extremos y equivalencia entre una palabra y apellido NULL. Usar comparaciones que detecten NULL explícitamente; comprobar tanto cero filas en datos válidos como que cada check detecta datos inválidos. No son migraciones ni constraints nuevos.
5. **Tests aislados.** `SchemaIT` y `CustomerNameMigrationIT` extienden `IntegrationTest` y usan bases aisladas en el PostgreSQL común, sin arrancar contenedores propios. Como los demás `*MigrationIT`, recrean la base con `DROP DATABASE IF EXISTS ... WITH (FORCE)` y `CREATE DATABASE`, ejecutan Flyway con la versión objetivo y eliminan la base al terminar. Una base vacía en el servidor común permite probar desde cero Flyway y Hibernate sin superar el objetivo de cuatro contenedores en el paso 2. `SchemaIT` migra desde cero, inspecciona el historial y pending, y crea un `EntityManagerFactory` contra esa misma base con Hibernate `validate` para todas las entidades JPA, sin permitir creación automática de esquema. El test de migración aplica hasta V10, inserta ejemplos, migra a V11 y ejecuta los checks. Una prueba separada inserta nombres válidos e inválidos, verifica mensaje y recuento, ausencia de columnas V11, historial hasta V10 y datos intactos; corrige la fila y comprueba el reintento. El registro real se verifica con el soporte de integración compartido y los mismos checks.
6. **Coexistencia operativa.** Los fixtures que escriben solo `name` se actualizan cuando deban satisfacer invariantes, sin cambiar guardianes protegidos sin autorización. Los escritores antiguos siguen pudiendo crear filas sin componentes: los checks detectan la divergencia y, antes de contraer, hay que retirar o actualizar todos los escritores y repetir el backfill si corresponde.

### Reglas de arquitectura

- ArchUnit debe conservar las reglas existentes: `ordering.domain` sin Spring/JPA y `application` sin dependencia de infraestructura; no introducir dependencias de identity en ordering.
- Identity consume únicamente la API pública `ordering.api.CustomerRegistrationFacade`; la regla de partición no depende de identity ni de persistencia.
- Los mapeos JPA permanecen en el mapper de infraestructura; no se añade lógica de conversión a la entidad.

## Risks / Trade-offs

- [Escritores antiguos omiten los nuevos campos] → Permitirlos durante expandir y ejecutar checks tras el despliegue; la coherencia global no está garantizada mientras convivan.
- [Diferencias entre motores al procesar caracteres] → Especificar U+0020 y contrastar resultados SQL/Java con casos compartidos, incluyendo caracteres no tratados como separadores.
- [Rollback del binario tras registros nuevos] → Las columnas adicionales son compatibles con el binario anterior, pero sus escrituras posteriores necesitarán backfill antes de contraer.
- [Nombre histórico inválido] → V11 falla sin cambios; no publicar datos personales en el error.

## Migration Plan

1. Desplegar V11 mediante Flyway con el código de doble escritura; conservar las lecturas de `name`. Durante el despliegue mixto, vigilar los checks y corregir omisiones antes de avanzar.
2. Si V11 falla por nombres vacíos, una persona identifica y corrige las filas afectadas con valores reales autorizados y vuelve a ejecutar la migración; verificar que el historial sigue en V10 antes del reintento. No usar valores inventados.
3. Si hay que volver al binario anterior después del éxito, conservar V11 y sus columnas; volver a ejecutar un backfill revisado antes de exigir las invariantes globales.
4. **Siguiente versión, fuera de este cambio:** después de retirar todos los escritores antiguos y verificar/backfillear los datos, cambiar las lecturas a los componentes y, en una versión posterior y revisada, dejar de escribir y eliminar `name` (contraer).
