package com.codeja.master_acdc.ordering.domain.model;

import java.util.UUID;

/** Raw command input; OrderLine validates it only after authorization and identity resolution. */
public record RequestedOrderLine(UUID productId, int quantity) {}
