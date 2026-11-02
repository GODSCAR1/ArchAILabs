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
