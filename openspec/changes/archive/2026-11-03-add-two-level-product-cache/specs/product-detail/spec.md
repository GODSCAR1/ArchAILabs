# Spec Delta

## ADDED Requirements

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
