# Aumentar TTL de L1 a 10 minutos

**Session ID:** ses_e5c1f3565ffecTp55XU3rDNUSh
**Created:** 3/11/2026, 19:06:20
**Updated:** 3/11/2026, 19:10:32

---

## User

Sube el TTL de L1 a 10 minutos, así hay más hits.

---

## Assistant (Build · GPT-6 Luna · 6.6s)

_Thinking:_



No he cambiado el TTL: el proyecto protege `CacheTtlPolicy` y exige detenerse y pedir autorización antes de modificarlo. Además, sus reglas actuales fijan el TTL de L1 por debajo de 30 segundos, así que 10 minutos también requiere revisar esa decisión de diseño y su especificación OpenSpec.

¿Me autorizas a preparar una propuesta OpenSpec para cambiar ese límite y el TTL de L1 a 10 minutos?

---

## User

[AC/DC contexto de sesión]
Rama: lab/s11-pruebas
Umbral PIT vigente: 75%
Hallazgos abiertos (no introduzcas más de estos tipos):
ninguno

---

## Assistant (Build · GPT-6 Luna · 4.3s)

_Thinking:_



Anotado: rama `lab/s11-pruebas`, umbral PIT del 75 % y ningún hallazgo abierto. Para continuar con el cambio de TTL, aún necesito autorización explícita: `CacheTtlPolicy` está protegida y 10 minutos contradice el límite vigente de L1 (<30 s).

---

## User

Si

---

## Assistant (Build · GPT-6 Luna · 2.3s)

Entendido. Prepararé una propuesta OpenSpec para cambiar el TTL de L1 a 10 minutos y el límite asociado. No tocaré código hasta que revises y apruebes la propuesta.

---

