# Spec Delta

## Purpose

Define la lectura de la ficha del producto y de sus reseñas, incluyendo su contenido completo para que la ficha se resuelva sin una lectura separada de reseñas.

## ADDED Requirements

### Requirement: Ficha de producto con reseñas completas
El sistema SHALL devolver la ficha de un producto con todas sus reseñas completas incluidas en la lectura agregada del producto.

#### Scenario: Consultar ficha con reseñas
- **WHEN** el cliente solicita la ficha de un SKU existente que tiene reseñas
- **THEN** el sistema responde `200` con los datos de la ficha y todas las reseñas del producto, sin una lectura adicional de la colección de reseñas

#### Scenario: Consultar ficha sin reseñas
- **WHEN** el cliente solicita la ficha de un SKU existente que no tiene reseñas
- **THEN** el sistema responde `200` con los datos de la ficha y una lista vacía de reseñas

### Requirement: Reseñas paginadas desde la ficha
El sistema SHALL permitir consultar reseñas paginadas de un producto usando las reseñas completas incluidas en su documento, con orden por fecha descendente e id ascendente.

#### Scenario: Consultar página de reseñas
- **WHEN** el cliente solicita una página de reseñas de un SKU existente
- **THEN** el sistema responde `200` con los elementos y metadatos de página correspondientes, ordenados por fecha descendente e id ascendente

#### Scenario: SKU inexistente al consultar reseñas
- **WHEN** el cliente solicita reseñas paginadas para un SKU inexistente
- **THEN** el sistema responde `404` con `ProblemDetail` y no modifica productos ni stock

### Requirement: Rechazo al exceder el tamaño máximo del documento
El sistema SHALL rechazar la creación de una reseña si incluirla excede el tamaño máximo permitido para el documento del producto, preservando el producto y sus reseñas existentes.

#### Scenario: Reseña excede el máximo del documento
- **WHEN** el cliente intenta añadir una reseña que haría exceder el tamaño máximo del documento del producto
- **THEN** el sistema responde `413` con `ProblemDetail` cuyo `type` es `https://api.master-acdc.dev/problems/product-review-limit-exceeded` y no cambia el producto ni sus reseñas
