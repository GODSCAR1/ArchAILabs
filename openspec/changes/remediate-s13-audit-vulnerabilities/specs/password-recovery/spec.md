# Spec Delta

## Purpose

Permite recuperar acceso mediante un secreto privado y no predecible a partir de solicitudes de recuperación de otras cuentas.

## ADDED Requirements

### Requirement: Secreto de recuperación impredecible entre cuentas
El sistema SHALL generar secretos de recuperación con 256 bits de aleatoriedad criptográfica. Observar el secreto de una cuenta SHALL no permitir predecir el siguiente secreto de otra cuenta. SHALL conservar notificación privada, hash, expiración, consumo único y revocación vigentes.

#### Scenario: Token propio no permite predecir el siguiente token ajeno
- **GIVEN** dos cuentas habilitadas y solicitudes consecutivas sin generaciones intercaladas
- **WHEN** ambas solicitan POST `/api/auth/password-reset/request`, primero la cuenta propia y después la otra
- **THEN** ambas reciben HTTP 202 sin cuerpo ni secretos
- **AND** la predicción obtenida reconstruyendo el estado del generador débil desde el token propio no coincide con el token real de la otra cuenta, observado solo por el canal privado de prueba
- **AND** contraseñas, versiones de sesión, familias, pedidos y stock no cambian
