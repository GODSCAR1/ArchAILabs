package com.codeja.master_acdc.ordering.domain.model;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Raw command input; OrderLine validates it only after authorization and identity resolution. */
public record RequestedOrderLine(@Nullable UUID productId, int quantity) {}
