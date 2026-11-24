package com.codeja.master_acdc.ordering.domain.event;

public sealed interface OrderEvent permits OrderCancelled, OrderConfirmed {}
