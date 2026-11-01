package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import java.util.UUID;
import org.springframework.data.mongodb.core.mapping.Field;

public record CategoryRef(@Field("_id") UUID id, String name) {}
