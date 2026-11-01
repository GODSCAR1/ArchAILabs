package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("catalog_migration_checkpoints")
public record CatalogMigrationCheckpoint(
    @Id String id, UUID lastProcessedId, MigrationState state) {}
