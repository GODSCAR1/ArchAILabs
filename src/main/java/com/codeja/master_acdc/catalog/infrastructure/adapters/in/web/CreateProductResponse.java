package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import java.util.UUID;

/** Cuerpo de la respuesta de creación, con los valores ya normalizados. */
public record CreateProductResponse(UUID id, String name, long price, String sku, long version) {}
