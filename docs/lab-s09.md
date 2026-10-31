# Lab S09

## Paso 4 — paginación estable

### Experimento rojo: sin desempate por id

Con el desempate por id retirado temporalmente de `ProductSearchRepositoryAdapter`:

```text
bash ./mvnw -q -Dit.test=ProductSearchPaginationIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false verify
```

El test falló con este resultado de AssertJ:

```text
Expected size: 60 but was: 52
```

El mapa de ocurrencias mostraba ids repetidos, por ejemplo:

```text
85b8cfa4-01f8-3a53-b015-7bdf0ec70574=2L
ac795466-5b92-3f7a-bb86-e382b728de64=2L
b2b1d711-9408-32b9-8ef1-27f13908cb99=2L
```

Por tanto, al recorrer las páginas hubo productos ausentes y otros repetidos. El fallo
se produce porque el top-N heapsort no es estable cuando `LIMIT/OFFSET` ordena por
precios repetidos: PostgreSQL puede devolver los empates en distinto orden entre
páginas.

### Resultado verde: desempate por id restaurado

Se restauró el desempate determinista:

```java
Sort sort = Sort.by(direction, criteria.order().field())
    .and(Sort.by(Sort.Direction.ASC, "id"));
```

El mismo comando terminó correctamente:

```text
Process finished with exit code 0
```

## Paso 4 — número de consultas

### Experimento sin `fetchAssociations()`

Se comentó temporalmente la línea `filters.add(ProductSpecifications.fetchAssociations());`
y se ejecutó:

```text
bash ./mvnw -q -Dtest=ProductSearchQueryCountTest test
```

El test no llegó a la aserción AssertJ del número de sentencias. Falló antes con:

```text
org.hibernate.LazyInitializationException: Could not initialize proxy
[CategoryJpaEntity#00000000-0000-0000-0000-000000000001] - no session
```

Por ello, no hubo valores de sentencias esperadas frente a observadas: la conversión
de `CategoryJpaEntity.name()` intentó acceder a una asociación lazy fuera de la sesión.
La sentencia extra no pudo medirse porque el caso terminó antes de ejecutar la
aserción de consultas.

### Resultado verde con `fetchAssociations()` restaurado

Se restauró exactamente la línea:

```java
filters.add(ProductSpecifications.fetchAssociations());
```

El mismo comando terminó correctamente con el umbral original (`<= 2`):

```text
Process finished with exit code 0
```
