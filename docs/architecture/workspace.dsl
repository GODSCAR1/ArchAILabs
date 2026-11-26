workspace "Tienda online de electrónica" "Contexto, contenedores, componentes y flujos de la tienda" {
    !identifiers hierarchical

    configuration {
        scope softwaresystem
    }

    properties {
        // Las decisiones viven en docs/adr/ (formato propio, no adr-tools) y se enlazan desde README.md: no se duplican aquí.
        "structurizr.inspection.model.softwaresystem.decisions" "ignore"
    }

    model {
        cliente = person "Cliente" "Compra productos y consulta o cancela sus pedidos."
        gestor = person "Gestor de catálogo" "Gestiona productos, precios de venta, promociones y cupones."
        operario = person "Operario de almacén" "Gestiona mercancía, preparación de pedidos y revisión de devoluciones."
        analista = person "Analista de fraude" "Revisa pedidos retenidos por sospecha de fraude."
        contable = person "Contable" "Gestiona pérdidas por fraude y decisiones de reembolso."
        agente = person "Agente de atención" "Atiende incidencias y devoluciones de clientes."
        administrador = person "Administrador" "Gestiona cuentas de usuario y descarga exportaciones."

        pasarela = softwareSystem "Pasarela de pagos" "Autoriza pagos de pedidos; la notificación de contracargos está pendiente de integración." "External"
        transportista = softwareSystem "Transportista" "Recoge y entrega envíos y devoluciones." "External,Futuro"
        fabricante = softwareSystem "Fabricante o proveedor" "Comunica alertas de seguridad sobre productos." "External,Futuro"
        correo = softwareSystem "Proveedor de correo y SMS" "Envía comunicaciones a clientes." "External,Futuro"

        tienda = softwareSystem "Tienda online" "Gestiona el catálogo, los pedidos, el inventario y el acceso a la tienda." {
            !docs README.md
            api = container "Aplicación de la tienda" "Expone la API y ejecuta los módulos de la tienda." "Spring Boot 4.1.1, Java 25" {
                catalogo = component "Catálogo" "Productos, precios de venta y disponibilidad visible." "Java / Spring"
                inventario = component "Inventario" "Saldos y reservas de stock." "Java / Spring"
                pedidos = component "Pedidos" "Ciclo del pedido; incluye actualmente pagos y riesgo, el outbox y su relay." "Java / Spring"
                precios = component "Precios y promociones" "Cálculo de importes y gestión de promociones." "Java"
                identidad = component "Identidad y acceso" "Cuentas, autenticación y autorización." "Java / Spring Security"
                exportaciones = component "Exportaciones" "Descarga de archivos; módulo técnico, no bounded context." "Java / Spring"
            }
            postgres = container "PostgreSQL" "Guarda pedidos, clientes, intentos de pago y outbox de Pedidos; saldos de stock de Inventario; cuentas de usuario y tokens de Identidad. Catálogo lee el stock para su proyección." "PostgreSQL 17" "Database"
            mongo = container "MongoDB" "Guarda productos, reseñas y disponibilidad visible de Catálogo." "MongoDB 7" "Database"
            redis = container "Redis" "Guarda la caché de fichas de Catálogo y las versiones de sesión de Identidad; distribuye invalidaciones de caché entre instancias mediante pub/sub." "Redis 7" "Database"
            archivos = container "Archivos locales" "Contiene archivos de exportación que lee exports." "Sistema de archivos local" "Database"
        }

        operario -> tienda.api "Gestiona mercancía y prepara pedidos" "Protocolo por definir" "Futuro"
        analista -> tienda.api "Revisa pedidos sospechosos" "Protocolo por definir" "Futuro"
        contable -> tienda.api "Gestiona pérdidas por fraude" "Protocolo por definir" "Futuro"

        tienda.api -> transportista "Intercambia información de envíos y devoluciones" "Protocolo por definir" "Futuro"
        fabricante -> tienda.api "Comunica alertas de seguridad" "Protocolo por definir" "Futuro"
        tienda.api -> correo "Solicita comunicaciones a clientes" "Protocolo por definir" "Futuro"

        cliente -> tienda.api.catalogo "Busca productos y consulta fichas" "HTTP/JSON"
        cliente -> tienda.api.identidad "Se registra, inicia sesión y renueva su sesión" "HTTP/JSON"
        cliente -> tienda.api.pedidos "Crea, consulta, confirma y cancela sus pedidos" "HTTP/JSON"
        gestor -> tienda.api.catalogo "Gestiona productos y precios" "HTTP/JSON"
        gestor -> tienda.api.inventario "Reserva stock" "HTTP/JSON"
        agente -> tienda.api.pedidos "Consulta cualquier pedido y solicita reembolsos" "HTTP/JSON"
        administrador -> tienda.api.identidad "Gestiona cuentas" "HTTP/JSON"
        administrador -> tienda.api.exportaciones "Descarga exportaciones" "HTTP"
        consultaPrecios = tienda.api.pedidos -> tienda.api.catalogo "Consulta precios" "CatalogPrices (puerto publicado)"
        proyeccionStock = tienda.api.pedidos -> tienda.api.catalogo "Solicita actualizar la proyección de stock desde el relay" "CatalogStockProjection (puerto publicado)" "Deuda"
        tienda.api.pedidos -> tienda.api.inventario "Reserva stock" "InventoryFacade.reserve (puerto publicado)"
        tienda.api.pedidos -> tienda.api.inventario "Libera stock desde OrderCancelledListener" "InventoryFacade.release (puerto publicado; evento Spring interno)" "Deuda"
        tienda.api.pedidos -> tienda.api.precios "Calcula el subtotal" "Shared kernel OrderPricing, PricingOrder y PricingLine"
        tienda.api.identidad -> tienda.api.pedidos "Implementa el actor actual" "CurrentOrderActor (puerto publicado)"
        tienda.api.identidad -> tienda.api.pedidos "Registra clientes" "CustomerRegistrationFacade.register (puerto publicado)" "Deuda"
        tienda.api.catalogo -> tienda.api.inventario "Consulta su tabla stock, sin invocar el módulo" "SQL directo en PostgreSQL" "Deuda"
        tienda.api.catalogo -> tienda.mongo "Lee y escribe productos y disponibilidad; consulta reseñas" "Protocolo MongoDB"
        tienda.api.catalogo -> tienda.redis "Usa caché de fichas y distribuye invalidaciones" "RESP"
        tienda.api.inventario -> tienda.postgres "Lee y escribe saldos y reservas" "JPA / JDBC"
        tienda.api.pedidos -> tienda.postgres "Lee y escribe pedidos, intentos y outbox; el relay procesa confirmaciones" "JPA / JDBC"
        tienda.api.identidad -> tienda.postgres "Lee y escribe cuentas y tokens" "JPA / JDBC"
        tienda.api.catalogo -> tienda.postgres "Consulta stock de Inventario para proyectar disponibilidad" "SQL directo" "Deuda"
        tienda.api.identidad -> tienda.redis "Lee y publica versiones de sesión" "RESP"
        tienda.api.exportaciones -> tienda.archivos "Lee archivos de exportación" "Sistema de archivos"
        tienda.api.pedidos -> pasarela "Solicita autorización de pago" "HTTP/JSON"
    }

    views {
        systemContext tienda "Contexto" {
            include *
            autoLayout lr
        }
        container tienda "Contenedores" {
            include *
            autoLayout lr
        }
        component tienda.api "ComponentesAPI" {
            include *
            autoLayout lr
        }
        dynamic tienda.api "CheckoutConfirmado" "Confirmación de compra: pago autorizado" {
            cliente -> tienda.api.pedidos "Solicita confirmar la compra"
            tienda.api.pedidos -> tienda.postgres "Lee el pedido y busca un importe fijado"
            consultaPrecios "Si no hay importe fijado, consulta precios"
            tienda.api.pedidos -> tienda.api.precios "Calcula el subtotal"
            tienda.api.pedidos -> tienda.postgres "Fija el importe e inicia el intento"
            tienda.api.pedidos -> pasarela "Solicita autorización; la pasarela responde autorizado"
            tienda.api.pedidos -> tienda.postgres "Registra PAID y OrderConfirmed en el outbox, en una transacción"
            tienda.api.pedidos -> tienda.postgres "El relay programado lee el outbox"
            proyeccionStock "El relay solicita sincronizar la disponibilidad"
            tienda.api.catalogo -> tienda.postgres "Consulta el stock de Inventario"
            tienda.api.catalogo -> tienda.mongo "Actualiza la disponibilidad visible"
            tienda.api.pedidos -> tienda.postgres "El relay marca procesado el evento"
            autoLayout lr
        }
        dynamic tienda.api "CheckoutRechazado" "Confirmación de compra: pago rechazado" {
            cliente -> tienda.api.pedidos "Solicita confirmar la compra"
            tienda.api.pedidos -> tienda.postgres "Lee el pedido y busca un importe fijado"
            consultaPrecios "Si no hay importe fijado, consulta precios"
            tienda.api.pedidos -> tienda.api.precios "Calcula el subtotal"
            tienda.api.pedidos -> tienda.postgres "Fija el importe e inicia el intento"
            tienda.api.pedidos -> pasarela "Solicita autorización; la pasarela responde rechazado"
            tienda.api.pedidos -> tienda.postgres "Abandona el intento, vuelve a PENDING y conserva el importe"
            autoLayout lr
        }
        styles {
            element "Person" {
                shape Person
            }
            element "Database" {
                shape Cylinder
            }
            element "External" {
                background #999999
            }
            element "Futuro" {
                border dashed
            }
            relationship "Relationship" {
                dashed false
            }
            relationship "Futuro" {
                dashed true
            }
            relationship "Deuda" {
                color #d00000
            }
        }
    }
}
