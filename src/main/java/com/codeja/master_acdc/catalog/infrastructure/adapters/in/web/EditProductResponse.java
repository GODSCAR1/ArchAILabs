package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import java.util.UUID;

/** Cuerpo de la respuesta de edición, con la versión persistida. */
public record EditProductResponse(UUID id, String name, long price, long version) {}
