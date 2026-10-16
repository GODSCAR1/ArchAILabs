package com.codeja.master_acdc.infrastructure.adapters.in.web;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Cuerpo de la petición de creación. Valida presencia y tipo; las reglas viven en el dominio. */
public record CreateProductRequest(
    @NotNull String name, @NotNull String sku, @NotNull BigDecimal price) {}
