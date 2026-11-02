# Spec Delta

## MODIFIED Requirements

### Requirement: Documento agregado de catálogo

El almacén documental SHALL contener una ficha por SKU con producto, categoría, variantes, atributos, stock disponible, rating y como máximo cinco reseñas recientes; las reseñas completas SHALL vivir fuera de la ficha para permitir crecimiento ilimitado.

#### Scenario: Ficha agregada disponible
- **WHEN** se prepara la ficha de un producto
- **THEN** el documento de MongoDB contiene precio, estado, categoría `{_id, name}`, variantes, atributos, `availableStock`, rating y `latestReviews` con los valores correspondientes al producto

#### Scenario: Respuesta HTTP de ficha
- **WHEN** un cliente solicita la ficha de un SKU disponible
- **THEN** el sistema responde `200` con el contrato vigente, incluido el campo `stock` en lugar de exponer `availableStock`

#### Scenario: Arrays acotados
- **WHEN** la materialización encuentra un producto con más de cinco reseñas recientes o más de cien variantes
- **THEN** el mapper recorta `latestReviews` a cinco; no materializa el producto que excede cien variantes, lo cuenta como rechazado con su SKU en el informe y continúa con los demás productos del lote

#### Scenario: Reseñas completas paginadas
- **WHEN** el cliente solicita una página de reseñas de un SKU existente
- **THEN** el sistema responde `200` con la página y sus metadatos, ordenada por fecha descendente e id ascendente, sin cargar las reseñas completas dentro de la ficha

### Requirement: Validación e índices del almacén documental

El sistema SHALL aplicar un validador de esquema con los campos obligatorios, incluido `price`, los rangos y arrays acotados, y SHALL crear explícitamente los índices de SKU único y de categoría/precio; no SHALL crear índices automáticamente.

#### Scenario: Arranque con estructura válida
- **WHEN** MongoDB se inicializa
- **THEN** existe validación de esquema para los campos obligatorios, `price`, `variants <= 100` y `latestReviews <= 5`, además de los índices requeridos

#### Scenario: Documento inválido rechazado
- **WHEN** se intenta almacenar directamente una ficha sin un campo obligatorio, con `price` ausente o fuera del rango permitido, o con arrays por encima de sus límites
- **THEN** MongoDB rechaza el documento y no aumenta el recuento de fichas válidas

#### Scenario: Índices reproducibles
- **WHEN** el inicializador de MongoDB se ejecuta más de una vez
- **THEN** los índices requeridos siguen siendo únicos y no se crean índices adicionales por descubrimiento automático

## REMOVED Requirements

### Requirement: Selección compatible del almacén de lectura
**Reason**: MongoDB es el único almacén de lectura del catálogo; ya no existe selección compatible ni activación de infraestructura ligada a una migración.
**Migration**: Retirar `catalog.store` y `catalog.migration.enabled`; las lecturas del catálogo se sirven desde MongoDB.

### Requirement: Re-migración periódica desde PostgreSQL
**Reason**: La proyección periódica del catálogo desde el almacén relacional deja de ser necesaria al retirar ese almacén para el catálogo.
**Migration**: Conservar los datos autoritativos del catálogo en MongoDB; la proyección periódica de stock desde Inventory no forma parte de este requisito.

### Requirement: Migración reanudable e idempotente
**Reason**: Ya no existe un proceso de migración del catálogo que requiera checkpoint, reanudación o upsert entre almacenes.
**Migration**: No se requiere una acción de migración; MongoDB permanece como almacén único del catálogo.

### Requirement: Equivalencia entre almacenes
**Reason**: La equivalencia comparativa entre dos almacenes deja de ser un contrato cuando el catálogo solo usa MongoDB.
**Migration**: Mantener los contratos funcionales de búsqueda, ficha y reseñas sobre el almacén MongoDB.
