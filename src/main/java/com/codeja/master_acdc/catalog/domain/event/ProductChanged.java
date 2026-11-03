package com.codeja.master_acdc.catalog.domain.event;

/** Signals a product change that invalidates its cached detail. */
public record ProductChanged(String sku) {}
