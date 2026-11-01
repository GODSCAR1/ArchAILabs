# ADR 0007: buscador y concurrencia del catálogo

- **Estado:** aceptado
- **Contexto:** `add-product-search`

## Decisión

Se adopta la alternativa A: `Specification` por filtro, combinadas con
`Specification.allOf`, y fetches de categoría y stock solo en la consulta de
la página. La consulta de count no incluye fetches. No se añade Blaze-Persistence
ni una dependencia nueva.

## Comparación

La medición se realiza con PostgreSQL/Testcontainers y 120 productos, más que el
tamaño de batch de 50, después de limpiar las estadísticas de Hibernate.

| Alternativa | Consulta de página | Consulta de count | Resultado | Entidades de stock por fila |
|---|---:|---:|---:|---:|
| A. Specification + fetch condicional | 1 | 1 | 2 observado, umbral `<= 2` | 0 adicionales |
| B. Entity View de Blaze-Persistence | no medida (requiere dependencia nueva) | no medida (requiere dependencia nueva) | no medida (requiere dependencia nueva) | no medida (requiere dependencia nueva) |

La alternativa B no se ha medido porque requiere una dependencia nueva que no
forma parte del proyecto y cuya adición está fuera del alcance autorizado. La
alternativa A cumple el umbral medido,
mantiene la consulta parametrizada y usa las capacidades ya instaladas.

## Consecuencias

- El test de número de consultas debe conservar más de 50 filas y el umbral `<= 2`.
- Los fetches deben distinguir la consulta de página de la consulta de count.
- Si la consulta supera el umbral con el volumen objetivo, se deberá revisar este ADR antes de cambiar de estrategia.
