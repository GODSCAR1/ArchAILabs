package com.codeja.master_acdc.ordering.application.port;

import java.util.UUID;

public record RefundOrderCommand(UUID orderId) {}
