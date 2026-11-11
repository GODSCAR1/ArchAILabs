# static-security-analysis Specification

## Purpose
Ofrece controles estáticos locales reproducibles para prevenir los patrones inseguros del laboratorio y regresiones en identidad y registros.

## Requirements

### Requirement: Cobertura ejecutable de patrones inseguros del proyecto
El proyecto SHALL ofrecer reglas locales para ordenación inyectable, secretos con aleatoriedad débil, lectura de exports sin confinamiento, concatenación e inyección de consultas, selección pública de cliente y registro de secretos. Cada regla SHALL tener ejemplos positivos y negativos comprobables automáticamente.

#### Scenario: Fixtures detectan cada patrón y aceptan su remediación
- **WHEN** se ejecuta la suite de reglas locales sobre sus fixtures anotados
- **THEN** detecta las ubicaciones positivas esperadas de las siete reglas y no señala las negativas; la suite termina correctamente
- **AND** incluye los patrones exactos sembrados de S13-01, S13-02 y S13-03, no solo variantes más simples

### Requirement: Auditoría sin contaminación por fixtures de reglas
La auditoría ordinaria SHALL excluir los fixtures vulnerables de reglas con un motivo documentado, sin excluir código de producción. La ejecución explícita de sus tests SHALL seguir verificando todos los fixtures después de añadir la exclusión. Las reglas locales SHALL no encontrar los patrones remediados en producción.

#### Scenario: Exclusión de fixtures conserva tests y auditoría limpia
- **WHEN** se ejecutan los tests locales antes y después de la exclusión, y después la auditoría del código corregido
- **THEN** ambos tests verifican las mismas siete reglas con éxito, ningún fixture aparece en la auditoría y no hay hallazgos locales en producción
- **AND** pedidos y stock no cambian
