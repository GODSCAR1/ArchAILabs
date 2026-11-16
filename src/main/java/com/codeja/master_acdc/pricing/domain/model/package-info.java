/**
 * Modelo de precios: descuento por volumen, cupones, impuestos y redondeo del total.
 *
 * <p>Java puro: NUNCA importa Spring, JPA ni Jackson. Los importes se redondean a 2 decimales con
 * {@link java.math.RoundingMode#HALF_EVEN} en {@link Money}.
 */
@NullMarked
package com.codeja.master_acdc.pricing.domain.model;

import org.jspecify.annotations.NullMarked;
