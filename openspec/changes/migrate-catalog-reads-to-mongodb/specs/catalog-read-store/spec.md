# Spec Delta

## Purpose

Permite materializar y servir las lecturas del catálogo desde un almacén documental sin cambiar
los endpoints existentes, manteniendo PostgreSQL como fuente de verdad y una migración repetible,
reanudable y verificable.

## ADDED Requirements

### Requirement: Selección compatible del almacén de lectura

El sistema SHALL seleccionar PostgreSQL por defecto y SHALL permitir seleccionar MongoDB mediante
`catalog.read-store=mongo`, manteniendo las escrituras en PostgreSQL en ambos modos. La infraestructura
Mongo (inicializador, validador y job) SHALL activarse si `catalog.read-store=mongo` o
`catalog.migration.enabled=true`; el adaptador lector se selecciona independientemente.

#### Scenario: Lecturas por defecto en PostgreSQL
- **WHEN** no se configura `catalog.read-store`
- **THEN** `GET /api/products`, `GET /api/products/{sku}` y las reseñas paginadas responden `200` con el contrato vigente y leen la vista PostgreSQL del catálogo

#### Scenario: Lecturas activadas en MongoDB
- **WHEN** `catalog.read-store=mongo` está configurado y la ficha está materializada
- **THEN** los mismos endpoints responden `200` con el mismo payload, paginación y orden que en PostgreSQL, pero leen MongoDB

#### Scenario: Producto inexistente en ambos modos
- **WHEN** el cliente solicita la ficha o reseñas de un SKU inexistente en cualquiera de los modos
- **THEN** el sistema responde `404` con `ProblemDetail.type=https://api.master-acdc.dev/problems/product-not-found` y no modifica productos ni stock

#### Scenario: Infraestructura Mongo activada por lectura o migración
- **WHEN** se configura `catalog.read-store=mongo` o `catalog.migration.enabled=true`
- **THEN** se activan el inicializador, el validador y el job Mongo; con `read-store=postgres` y la migración habilitada las lecturas permanecen en PostgreSQL mientras Mongo se alimenta

### Requirement: Documento agregado de catálogo

El almacén documental SHALL contener una ficha por SKU con producto, categoría, variantes, atributos,
stock disponible, rating y como máximo cinco reseñas recientes; las reseñas completas SHALL vivir
fuera de la ficha para permitir crecimiento ilimitado.

#### Scenario: Ficha agregada disponible
- **WHEN** un producto se materializa desde PostgreSQL
- **THEN** el documento de MongoDB contiene precio, estado, categoría `{_id, name}`, variantes, atributos, `availableStock`, rating y `latestReviews` con los valores derivados de PostgreSQL

#### Scenario: Respuesta HTTP equivalente a PostgreSQL
- **WHEN** un cliente solicita la ficha de un SKU materializado en modo Mongo
- **THEN** el sistema responde `200` con el mismo contrato y valores HTTP que en modo PostgreSQL, incluido el campo `stock` en lugar de exponer `availableStock`

#### Scenario: Arrays acotados
- **WHEN** la migración encuentra un producto con más de cinco reseñas recientes o más de cien variantes
- **THEN** el mapper recorta `latestReviews` a cinco; no materializa el producto que excede cien variantes, lo cuenta como rechazado con su SKU en el informe y continúa con los demás productos del lote

#### Scenario: Reseñas completas paginadas
- **WHEN** el cliente solicita una página de reseñas de un SKU migrado
- **THEN** el sistema responde `200` con la página y sus metadatos, ordenada por fecha descendente e id ascendente, sin cargar las reseñas completas dentro de la ficha

### Requirement: Validación e índices del almacén documental

El sistema SHALL aplicar al arrancar un validador de esquema con los campos obligatorios, incluido
`price`, los rangos y arrays acotados, y SHALL crear explícitamente los índices de SKU único y de
categoría/precio; no SHALL crear índices automáticamente.

#### Scenario: Arranque con estructura válida
- **WHEN** la infraestructura MongoDB se activa y se inicializa
- **THEN** existe validación de esquema para los campos obligatorios, `price`, `variants <= 100` y `latestReviews <= 5`, además de los índices requeridos

#### Scenario: Documento inválido rechazado
- **WHEN** se intenta almacenar directamente una ficha sin un campo obligatorio, con `price` ausente o fuera del rango permitido, o con arrays por encima de sus límites
- **THEN** MongoDB rechaza el documento y no aumenta el recuento de fichas válidas

#### Scenario: Índices reproducibles
- **WHEN** el inicializador de MongoDB se ejecuta más de una vez
- **THEN** los índices requeridos siguen siendo únicos y no se crean índices adicionales por descubrimiento automático

### Requirement: Re-migración periódica desde PostgreSQL

El sistema SHALL ejecutar un barrido completo por lotes configurable y desactivable desde
PostgreSQL, actualizando categoría, stock disponible, rating, reseñas recientes y reseñas completas.

#### Scenario: Migración habilitada
- **WHEN** `catalog.migration.enabled=true` y llega el intervalo configurado
- **THEN** el sistema recorre todos los productos PostgreSQL por lotes, sin detección de productos modificados, actualiza sus vistas en MongoDB y devuelve un informe con productos, variantes, reseñas y rechazos procesados

#### Scenario: Migración desactivada
- **WHEN** `catalog.migration.enabled=false` o la propiedad no está activada
- **THEN** no se ejecutan lotes periódicos ni se modifican documentos por efecto del job

#### Scenario: Copia de stock y categoría
- **WHEN** un ciclo contiene un producto cuyo stock disponible o nombre de categoría cambió desde el ciclo anterior
- **THEN** la ficha materializada se actualiza con esos valores copiados, mientras PostgreSQL conserva la propiedad de las escrituras

#### Scenario: Ciclo siguiente tras un barrido completo
- **WHEN** un ciclo termina con checkpoint `{lastProcessedId, estado=COMPLETADO}` y llega el siguiente intervalo
- **THEN** el sistema reinicia el cursor desde el inicio, marca el nuevo barrido `EN_CURSO` y vuelve a recorrer todos los productos, propagando cambios ocurridos desde el ciclo anterior

### Requirement: Migración reanudable e idempotente

La migración SHALL usar upsert por SKU y keyset sobre UUID, persistiendo un checkpoint con
`lastProcessedId` y estado `EN_CURSO` o `COMPLETADO`; nunca SHALL paginar con OFFSET.

#### Scenario: Reanudación tras interrupción
- **WHEN** la migración se interrumpe con estado `EN_CURSO` después de guardar un lote confirmado
- **THEN** la siguiente ejecución continúa con UUID mayor que `lastProcessedId` y no depende de OFFSET

#### Scenario: Ejecución repetida
- **WHEN** se repite la migración sobre los mismos datos PostgreSQL
- **THEN** los recuentos de productos materializados, variantes y reseñas en MongoDB permanecen iguales y no se crean duplicados

#### Scenario: Informe de migración
- **WHEN** finaliza una ejecución completa o reanudada
- **THEN** el informe identifica productos, variantes, reseñas, productos rechazados con sus SKU y el checkpoint alcanzado

### Requirement: Equivalencia entre almacenes

El sistema SHALL conservar equivalencia funcional entre las lecturas PostgreSQL y MongoDB para el
buscador, la ficha y las reseñas, incluyendo rating con dos decimales y redondeo `HALF_UP`.

#### Scenario: Recuentos equivalentes
- **WHEN** termina una migración completa sin rechazos
- **THEN** los recuentos de productos, variantes y reseñas coinciden entre ambos almacenes

#### Scenario: Fichas equivalentes
- **WHEN** se comparan doscientos SKU elegidos aleatoriamente después de migrar
- **THEN** los `ProductDetail` construidos desde ambos almacenes son iguales y sus promedios de rating se comparan por valor decimal, no por representación

#### Scenario: Buscador equivalente
- **WHEN** se ejecuta la suite de búsquedas con cada valor de `catalog.read-store`
- **THEN** filtros, ordenación, paginación, disponibilidad y metadatos producen los mismos resultados de dominio que PostgreSQL, incluidos texto con caracteres especiales y orden por nombre con mayúsculas, minúsculas y acentos
