package com.codeja.master_acdc.ordering.domain.model;

import java.math.BigDecimal;

/** Importe fijado y número de intento enviado a la pasarela. */
public record PaymentAttempt(BigDecimal amount, int number) {}
