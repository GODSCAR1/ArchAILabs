workspace "Tienda online de electrónica" "Contexto y contenedores de la tienda" {
    !identifiers hierarchical

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
            api = container "Aplicación de la tienda" "Expone la API y ejecuta los módulos de la tienda." "Spring Boot 4.1.1, Java 25"
            postgres = container "PostgreSQL" "Guarda pedidos, clientes, intentos de pago y outbox de Pedidos; saldos de stock de Inventario; cuentas de usuario y tokens de Identidad. Catálogo lee el stock para su proyección." "PostgreSQL 17" "Database"
            mongo = container "MongoDB" "Guarda productos, reseñas y disponibilidad visible de Catálogo." "MongoDB 7" "Database"
            redis = container "Redis" "Guarda la caché de fichas de Catálogo y las versiones de sesión de Identidad; distribuye invalidaciones de caché entre instancias mediante pub/sub." "Redis 7" "Database"
            archivos = container "Archivos locales" "Contiene archivos de exportación que lee exports." "Sistema de archivos local" "Database"
        }

        cliente -> tienda.api "Se registra, compra y consulta o cancela pedidos" "HTTP/JSON"
        gestor -> tienda.api "Gestiona productos y precios" "HTTP/JSON"
        operario -> tienda.api "Gestiona mercancía y prepara pedidos" "Protocolo por definir" "Futuro"
        analista -> tienda.api "Revisa pedidos sospechosos" "Protocolo por definir" "Futuro"
        contable -> tienda.api "Gestiona pérdidas por fraude" "Protocolo por definir" "Futuro"
        agente -> tienda.api "Consulta pedidos y solicita reembolsos" "HTTP/JSON"
        administrador -> tienda.api "Gestiona cuentas de usuario y descarga exportaciones" "HTTP"

        tienda.api -> tienda.postgres "Lee y escribe pedidos, stock y cuentas; consulta stock para Catálogo" "JDBC"
        tienda.api -> tienda.mongo "Lee y escribe productos y disponibilidad visible; consulta reseñas" "Protocolo MongoDB"
        tienda.api -> tienda.redis "Lee y escribe fichas en caché y versiones de sesión; publica y recibe invalidaciones de caché" "RESP"
        tienda.api -> tienda.archivos "Lee archivos de exportación" "Sistema de archivos"
        tienda.api -> pasarela "Solicita la autorización de pagos" "HTTP/JSON"
        tienda.api -> transportista "Intercambia información de envíos y devoluciones" "Protocolo por definir" "Futuro"
        fabricante -> tienda.api "Comunica alertas de seguridad" "Protocolo por definir" "Futuro"
        tienda.api -> correo "Solicita comunicaciones a clientes" "Protocolo por definir" "Futuro"
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
        }
    }
}
