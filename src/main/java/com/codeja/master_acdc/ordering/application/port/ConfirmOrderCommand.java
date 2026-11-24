package com.codeja.master_acdc.ordering.application.port;

import java.util.UUID;

public record ConfirmOrderCommand(UUID orderId, UUID customerId) {}
