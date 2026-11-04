# product-detail Specification

## Purpose
Permite consultar una ficha de producto completa y sus reseñas paginadas para presentar información de compra sin exponer modelos internos ni convertir la lectura en una secuencia de consultas por elemento.

## Requirements

### Requirement: Consulta de ficha por SKU

El sistema SHALL exponer `GET /api/products/{sku}` y devolver id, SKU, nombre, precio, estado, categoría, stock disponible, variantes, atributos, valoración y las reseñas más recientes del producto. `rating.average` SHALL ser un decimal con escala de 2 cifras, calculado en SQL como `round(avg(rating)::numeric, 2)` con redondeo HALF_UP, y SHALL valer `0` cuando no haya reseñas.

#### Scenario: Ficha existente con todos sus datos
- **WHEN** el cliente solicita `GET /api/products/SKU-CAFE` y el producto tiene categoría, stock, variantes, atributos y reseñas
- **THEN** el sistema responde `200` con `ProductDetailResponse`, incluyendo `category {id, name}`, `stock`, todas las variantes y un objeto de atributos cuya clave es `name` y cuyo valor es `value`

#### Scenario: Producto sin stock ni datos opcionales
- **WHEN** el cliente solicita la ficha de un producto que no tiene registro de stock, variantes, atributos ni reseñas
- **THEN** el sistema responde `200`, informa `stock=0`, devuelve listas y mapa vacíos, e informa `rating.average=0` y `rating.count=0`

#### Scenario: Promedio con decimales
- **WHEN** el producto tiene reseñas con valoraciones `5`, `4` y `4`
- **THEN** el sistema responde `200` e informa `rating.average=4.33` y `rating.count=3`

#### Scenario: SKU inexistente en ficha
- **WHEN** el cliente solicita `GET /api/products/SKU-INEXISTENTE` y no existe un producto con ese SKU
- **THEN** el sistema responde `404` con un `ProblemDetail` cuyo `type` es `https://api.master-acdc.dev/problems/product-not-found`

#### Scenario: Límite de reseñas recientes
- **WHEN** el cliente solicita la ficha de un producto con más de cinco reseñas
- **THEN** el sistema responde `200` con como máximo cinco reseñas, ordenadas por `createdAt` descendente y desempatadas por `id` de forma determinista, mientras `rating` cuenta y promedia todas las reseñas

### Requirement: Lectura de variantes y atributos

El sistema SHALL devolver los datos descriptivos asociados al producto.

#### Scenario: Variantes y atributos pertenecen al producto
- **WHEN** el cliente solicita una ficha que tiene variantes y atributos asociados
- **THEN** el sistema responde `200` incluyendo cada variante con id, sufijo SKU, color, talla y precio adicional en COP, y cada atributo una sola vez por nombre

### Requirement: Consultas de reseñas paginadas

El sistema SHALL exponer `GET /api/products/{sku}/reviews` con página cero-indexada, `size=20` por defecto, máximo 100 y orden estable por `createdAt` descendente seguido de `id` ascendente.

#### Scenario: Primera página de reseñas
- **WHEN** el cliente solicita `GET /api/products/SKU-CAFE/reviews` para un producto existente
- **THEN** el sistema responde `200` con una página de reseñas de `size=20` como máximo y metadatos `page`, `size`, `totalElements` y `totalPages`

#### Scenario: Tamaño superior al máximo
- **WHEN** el cliente solicita `GET /api/products/SKU-CAFE/reviews?page=0&size=1000`
- **THEN** el sistema responde `200` normalizando el tamaño a `100` como máximo

#### Scenario: Desempate de reseñas
- **WHEN** dos reseñas de un producto tienen el mismo `createdAt`
- **THEN** el sistema responde `200` colocándolas según `id` ascendente después del orden descendente por fecha

#### Scenario: Paginación de reseñas inválida
- **WHEN** el cliente solicita reseñas con `page < 0` o `size < 1`
- **THEN** el sistema responde `400` con el mismo tipo de problema y parámetro que el buscador mediante `InvalidSearchParameterException`

#### Scenario: SKU inexistente en reseñas
- **WHEN** el cliente solicita `GET /api/products/SKU-INEXISTENTE/reviews`
- **THEN** el sistema responde `404` con un `ProblemDetail` cuyo `type` es `https://api.master-acdc.dev/problems/product-not-found`

### Requirement: Coste fijo de la ficha

El sistema SHALL resolver la ficha en una sola operación de lectura sobre el almacén documental, sin que el número de variantes, atributos o reseñas cambie ese número; las consultas de la página completa de reseñas no forman parte de este límite.

#### Scenario: Muchas colecciones asociadas
- **WHEN** el cliente solicita la ficha de un producto con muchas variantes, atributos y reseñas
- **THEN** el sistema responde `200` y resuelve la ficha con una sola operación de lectura sobre el almacén documental

### Requirement: Compatibilidad de rutas de producto

El sistema SHALL conservar `PUT /api/products/{id}` con identificador UUID y SHALL resolver `GET /api/products/{sku}` por SKU sin cambiar el contrato del buscador `GET /api/products`.

#### Scenario: Edición UUID y consulta SKU coexistentes
- **WHEN** el cliente usa `PUT /api/products/<uuid>` para editar y `GET /api/products/SKU-CAFE` para consultar
- **THEN** cada operación se enruta a su contrato correspondiente, la edición usa UUID, la consulta usa SKU y el buscador no cambia

### Requirement: La ficha de producto usa una cache de dos niveles sin alterar el contrato HTTP
El sistema SHALL almacenar únicamente el valor inmutable de la ficha usando el SKU como clave explícita, un nivel local con TTL de 15 s ±10% y capacidad máxima de 10.000 entradas, y un almacén compartido con TTL de 10 min ±10%. El nivel local SHALL caducar como máximo a los 16,5 s y las cargas concurrentes de una misma ficha SHALL ejecutarse una sola vez por instancia.

#### Scenario: Ficha repetida
- **GIVEN** la ficha `SKU-CAFE` está cacheada con precio `10000`
- **WHEN** el cliente solicita dos veces `GET /api/products/SKU-CAFE`
- **THEN** ambas respuestas son HTTP 200 con precio `10000` y la segunda lectura no consulta el repositorio de fichas

#### Scenario: Lectura desde el almacén compartido
- **GIVEN** el almacén compartido contiene `SKU-CAFE` con precio `10000` y el nivel local no contiene esa ficha
- **WHEN** el cliente solicita `GET /api/products/SKU-CAFE`
- **THEN** la respuesta es HTTP 200 con precio `10000` y no se consulta el repositorio de fichas

#### Scenario: SKU inexistente se crea
- **GIVEN** `SKU-NUEVO` no existe y `GET /api/products/SKU-NUEVO` responde HTTP 404 con ProblemDetail type `https://api.master-acdc.dev/problems/product-not-found`
- **WHEN** `POST /api/products` crea `SKU-NUEVO` con precio `7500` y responde HTTP 201, y después se solicita `GET /api/products/SKU-NUEVO`
- **THEN** la respuesta es HTTP 200 con precio `7500` sin esperar a ningún TTL

#### Scenario: Almacén compartido no disponible durante una lectura
- **GIVEN** `SKU-CAFE` existe con precio `10000` y el almacén compartido no está disponible
- **WHEN** el cliente solicita `GET /api/products/SKU-CAFE`
- **THEN** la respuesta es HTTP 200 con precio `10000`, el fallo se registra como miss y no se propaga un error al cliente

#### Scenario: Edición propagada entre instancias
- **GIVEN** las instancias A y B tienen cacheado `SKU-CAFE` con precio `10000` y la edición válida cambia el precio a `12000`
- **WHEN** ambas instancias reciben la invalidación y solicitan `GET /api/products/SKU-CAFE`
- **THEN** ambas responden HTTP 200 con precio `12000`, normalmente en menos de un segundo

#### Scenario: Mensaje de invalidación perdido
- **GIVEN** la instancia B tiene cacheado `SKU-CAFE` con precio `10000` y la instancia A lo edita a `12000`, pero B no recibe el mensaje de invalidación
- **WHEN** se solicita la ficha en B y vence su entrada local
- **THEN** B responde HTTP 200 con precio `12000` antes de 30 segundos

#### Scenario: Reseñas paginadas
- **GIVEN** existe `SKU-CAFE` con precio `10000` y reseñas paginadas disponibles
- **WHEN** el cliente solicita `GET /api/products/SKU-CAFE/reviews`
- **THEN** la respuesta HTTP 200 conserva la paginación existente y el resultado no se almacena en la cache de fichas

#### Scenario: Concurrencia sobre una ficha ausente de cache
- **GIVEN** `SKU-CAFE` existe con precio `10000` y no está cacheado en una instancia
- **WHEN** 200 hilos virtuales de esa instancia solicitan simultáneamente `GET /api/products/SKU-CAFE`
- **THEN** todas las respuestas son HTTP 200 con precio `10000` y se realiza una sola carga de origen

#### Scenario: Valor recuperado del almacén compartido
- **GIVEN** la ficha original de `SKU-CAFE` contiene precio `10000`, variantes, atributos y valoración
- **WHEN** la ficha se guarda y se recupera del almacén compartido
- **THEN** el valor recuperado es igual al original, incluidos sus campos y colecciones

#### Scenario: TTL aleatorios
- **GIVEN** se cachean `SKU-CAFE` con precio `10000` y `SKU-TE` con precio `12000` en ambos niveles
- **WHEN** se comparan con sus TTL base de 15 s y 10 min
- **THEN** cada vencimiento está dentro de ±10% de su base y los valores no son todos iguales

#### Scenario: Métricas por nivel
- **GIVEN** se leyó `SKU-CAFE` con precio `10000` desde el nivel local y `SKU-TE` con precio `12000` desde el almacén compartido
- **WHEN** se consultan las métricas de cache disponibles
- **THEN** las lecturas de cada nivel se pueden medir por separado
