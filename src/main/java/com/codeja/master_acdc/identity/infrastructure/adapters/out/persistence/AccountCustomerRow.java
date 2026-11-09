package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import java.util.UUID;

public record AccountCustomerRow(UUID id, UUID customerId) {}
