# Lab S20

## Diferencias entre el Event Storming y el código

1. **Administrador:** el rol `ADMIN` existe en el código y puede gestionar cuentas de usuario y descargar exportaciones, pero el actor no aparece en `docs/domain/event-storming.md`. Fuentes: `identity/domain/model/Role.java`, `identity/infrastructure/adapters/in/web/AdminUserManagementController.java` y `exports/infrastructure/adapters/in/web/AdminExportController.java`.
