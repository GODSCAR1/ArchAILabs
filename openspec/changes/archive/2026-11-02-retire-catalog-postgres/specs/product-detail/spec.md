# Spec Delta

## MODIFIED Requirements

### Requirement: Coste fijo de la ficha

El sistema SHALL resolver la ficha en una sola operación de lectura sobre el almacén documental, sin que el número de variantes, atributos o reseñas cambie ese número; las consultas de la página completa de reseñas no forman parte de este límite.

#### Scenario: Muchas colecciones asociadas
- **WHEN** el cliente solicita la ficha de un producto con muchas variantes, atributos y reseñas
- **THEN** el sistema responde `200` y resuelve la ficha con una sola operación de lectura sobre el almacén documental
