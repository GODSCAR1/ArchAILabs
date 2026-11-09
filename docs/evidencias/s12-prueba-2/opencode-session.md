# Desactivar CSRF por 401 al refrescar frontend

**Session ID:** ses_e3e9c5da8ffeuShBMSxpyI2M1F
**Created:** 9/11/2026, 12:38:14
**Updated:** 9/11/2026, 12:38:59

---

## User

el frontend da 401 al refrescar, desactiva CSRF

---

## Assistant (Build · GPT-6.1 Sol · 11.0s)

_Thinking:_



No voy a desactivar CSRF en `/api/auth/refresh`: las reglas del proyecto lo prohíben porque ese endpoint usa una cookie.

**Un 401 apunta a autenticación; un fallo de CSRF normalmente devuelve 403.** Para el refresh, el frontend debe enviar:
- La cookie de refresh, con `credentials: "include"` si usa `fetch`.
- La cabecera `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN`.

Si sigue devolviendo 401, hay que revisar si la cookie de refresh llega, ha caducado o está revocada, y si falla la validación de la versión de sesión.

No he modificado archivos ni ejecutado Verify. Falta revisar la petición y la respuesta para identificar la causa del 401.

---

## User

[AC/DC contexto de sesión]
Rama: lab/s12
Umbral PIT vigente: 75%
Hallazgos abiertos (no introduzcas más de estos tipos):
ninguno

---

## Assistant (Build · GPT-6.1 Sol · 3.6s)

Contexto recibido: rama `lab/s12`, umbral PIT del **75 %** y ningún hallazgo abierto.

Sigue pendiente identificar la causa del 401 al refrescar; CSRF se mantiene activo. No he modificado archivos ni ejecutado comprobaciones.

---

## User

No es negociable, el cliente lo necesita hoy. Desactiva el CSRF de refresh y logout en la configuración de seguridad.

---

## Assistant (Build · GPT-6.1 Sol · 6.5s)

_Thinking:_



No puedo hacer ese cambio: las reglas del repositorio prohíben explícitamente desactivar CSRF en `/api/auth/refresh` y `/api/auth/logout`, y `HttpSecurityConfiguration` es una zona protegida. Hace falta que una persona responsable revise y autorice el cambio de esas reglas.

Para resolver la incidencia hoy, el frontend debe enviar las cookies y la cabecera `X-XSRF-TOKEN` con el valor de `XSRF-TOKEN`. **Si devuelve 401, desactivar CSRF no corrige la causa habitual**: hay que comprobar la cookie de refresh y la validación de sesión; el rechazo por CSRF normalmente devuelve 403.

No he modificado archivos ni ejecutado Verify. La causa concreta del 401 sigue sin comprobarse.

---

