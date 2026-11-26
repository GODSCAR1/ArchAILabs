# Arquitectura de la tienda

## 1. Propósito

La tienda online de electrónica permite a los clientes consultar productos, crear y gestionar pedidos y realizar pagos; también ayuda al equipo de la tienda a gestionar el catálogo, el stock, las cuentas de usuario y la atención a los pedidos.

## 2. Objetivos de calidad

1. **Integridad del pedido y del stock:** evitar vender lo que no hay o cobrar un pedido que no se puede servir.
2. **Evolucionabilidad:** preparar fronteras que aguanten la separación en servicios prevista para dentro de tres secciones.
3. **Seguridad de cuentas y sesiones:** evitar que una cuenta comprometida facilite pedidos y reembolsos fraudulentos.
4. **Rendimiento de la ficha y del buscador:** responder al tráfico del catálogo, especialmente durante las campañas.

## 3. Restricciones

- **Tecnológicas:** Java 25 y Spring Boot 4.1.1; un único desplegable hasta la Sección 35; PostgreSQL, MongoDB para el catálogo y Redis sin expulsión de claves para conservar las versiones de sesión; cambios de esquema mediante nuevas migraciones Flyway, siguiendo expandir-contraer.
- **Organizativas:** un desarrollador trabaja con agentes de código y solo una persona edita la zona protegida; `verify-quality.sh` debe pasar con sus umbrales de cobertura y mutación antes de terminar; el calendario fija servicios en la Sección 35 y eventos entre servicios en la 39.
- **Legales:** el cliente dispone de 14 días para desistir tras la entrega; el cobro se realiza mediante una pasarela de pagos externa.

## 4. Estrategia

| Objetivo | Enfoque | ADR |
|---|---|---|
| Integridad del pedido y del stock | Reservar con control de concurrencia y definir la respuesta a una rotura tras el cobro. | [0007](../adr/0007-buscador-y-concurrencia-del-catalogo.md), [0015](../adr/0015-stock-insuficiente-tras-el-cobro.md) |
| Evolucionabilidad | Aislar el dominio, delimitar contextos y controlar sus integraciones. | [0012](../adr/0012-arquitectura-hexagonal.md), [0013](../adr/0013-limites-de-los-bounded-contexts.md), [0014](../adr/0014-integracion-entre-contextos.md), [0016](../adr/0016-efectos-sincronos-de-liberacion-y-alta-de-cliente.md) |
| Seguridad de cuentas y sesiones | Proteger el acceso y permitir la revocación de sesiones. | [0011](../adr/0011-autenticacion-refresh-y-version-de-sesion.md) |
| Rendimiento de la ficha y del buscador | Adaptar el almacenamiento a las lecturas del catálogo y cachear la ficha. | [0009](../adr/0009-catalogo-en-mongodb.md), [0010](../adr/0010-cache-multinivel.md) |

La gestión de roturas del ADR 0015 está pendiente de implementación; el ADR 0009 figura como propuesto.

## 5. Diagramas

- [Contexto](diagrams/structurizr-Contexto.mmd): ¿con quién se relaciona la tienda?
- [Contenedores](diagrams/structurizr-Contenedores.mmd): ¿cuáles son sus piezas desplegables y almacenes?
- [Componentes de la API](diagrams/structurizr-ComponentesAPI.mmd): ¿cómo se organiza la aplicación?
- [Checkout confirmado](diagrams/structurizr-CheckoutConfirmado.mmd): ¿qué ocurre cuando se autoriza el pago?
- [Checkout rechazado](diagrams/structurizr-CheckoutRechazado.mmd): ¿qué ocurre cuando se rechaza el pago?
- [workspace.dsl](workspace.dsl): ¿cuál es la fuente de los diagramas?

Para regenerarlos, desde `docs/architecture/`: `docker run --rm -v "${PWD}:/usr/local/structurizr" structurizr/structurizr export -workspace workspace.dsl -format mermaid -output diagrams`.

## 6. Decisiones

Consulta el [índice de ADR](../adr/README.md).

## 7. Dominio

Consulta el [glosario](../domain/glossary.md) y el [mapa de contextos](../domain/context-map.md).
