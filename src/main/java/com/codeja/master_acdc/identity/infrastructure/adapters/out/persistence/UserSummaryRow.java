package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import java.util.UUID;

public record UserSummaryRow(UUID id, String email, String[] roles, boolean enabled) {}
