package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Cuerpo de la petición de edición. Las reglas de nombre y precio viven en el dominio. */
public record EditProductRequest(
    @NotNull String name, @NotNull BigDecimal price, @NotNull Long version) {}
