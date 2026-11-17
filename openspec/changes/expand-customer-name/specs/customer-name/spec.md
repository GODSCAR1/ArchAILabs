# Spec Delta

## Purpose

Conservar el nombre original del cliente y disponer de sus componentes coherentes durante la transición del modelo de datos.

## ADDED Requirements

### Requirement: Partición compatible del nombre de cliente
El sistema SHALL conservar el nombre original y derivar el primer nombre y el resto del nombre eliminando espacios U+0020 de los extremos y reduciendo cada secuencia interna de esos espacios a uno. La primera palabra será el primer nombre; el resto será el apellido, o NULL si no hay más palabras. Los demás caracteres SHALL conservarse.

#### Scenario: Nombre histórico de una palabra
- **WHEN** se amplía la representación de un cliente cuyo nombre es `  Ana  `
- **THEN** el original permanece `  Ana  `, el primer nombre es `Ana` y el apellido es NULL

#### Scenario: Nombre histórico con varias palabras
- **WHEN** se amplía la representación de un cliente cuyo nombre es `  María   del Mar  `
- **THEN** el original permanece intacto, el primer nombre es `María` y el apellido es `del Mar`

#### Scenario: Caracteres y longitud límite se conservan
- **WHEN** se amplía la representación de nombres con tildes, ñ, guiones, apóstrofos o 120 caracteres
- **THEN** se conservan los caracteres y el nombre original y se aplican las mismas reglas de partición

### Requirement: Registro mantiene ambas representaciones
Al crear un cliente, el sistema SHALL guardar el nombre original y sus componentes aplicando la misma partición que a los datos históricos, sin cambiar el contrato de registro ni el nombre mostrado en las lecturas existentes.

#### Scenario: Registro con nombre y espacios
- **WHEN** se registra una cuenta válida con nombre `  Ana   María  `
- **THEN** responde HTTP 201 y el cliente conserva el original y contiene primer nombre `Ana` y apellido `María`

#### Scenario: Registro sin nombre explícito conserva el valor derivado
- **WHEN** se registra una cuenta válida con `Cliente@Example.com` y sin nombre
- **THEN** responde HTTP 201 y el cliente conserva `cliente` como nombre original y primer nombre, con apellido NULL

### Requirement: Comprobación de coherencia del nombre
El sistema SHALL ofrecer comprobaciones que identifiquen cada cliente que incumpla las invariantes: primer nombre presente; componentes unidos por un espacio iguales al original normalizado; componentes sin espacios extremos; y apellido NULL si y solo si el original normalizado tiene una palabra.

#### Scenario: Comprobaciones de clientes coherentes
- **WHEN** se comprueban clientes históricos migrados y clientes recién registrados con nombres válidos
- **THEN** ninguna comprobación devuelve clientes infractores

#### Scenario: Comprobaciones detectan infracciones
- **WHEN** se introduce por separado cada clase de incoherencia de nombre
- **THEN** la comprobación correspondiente devuelve el cliente infractor

### Requirement: Migración rechaza nombres históricos vacíos
La ampliación SHALL fallar de forma atómica cuando encuentre nombres históricos vacíos o compuestos solo por espacios, indicando cuántos requieren corrección humana. SHALL no asignar nombres de sustitución ni modificar datos o esquema.

#### Scenario: Nombre histórico vacío detiene la migración
- **WHEN** se intenta ampliar la representación con al menos un nombre histórico vacío o solo de espacios
- **THEN** falla con un mensaje que indica el número de filas y que una persona debe corregirlas; el esquema y todos los datos, incluidos pedidos y stock, permanecen sin cambios

#### Scenario: Reintento tras corrección
- **WHEN** una persona corrige los nombres históricos inválidos y vuelve a ejecutar la ampliación
- **THEN** la ampliación termina y los nombres corregidos se dividen conforme a la regla

### Requirement: Esquema completo verificable desde cero
El esquema completo SHALL poder construirse desde cero sin migraciones pendientes ni fallidas y SHALL ser compatible con el modelo persistente de la aplicación.

#### Scenario: Esquema nuevo coherente
- **WHEN** se construye un esquema vacío con todas las migraciones disponibles
- **THEN** ninguna queda pendiente ni fallida y el modelo persistente valida contra el esquema resultante
