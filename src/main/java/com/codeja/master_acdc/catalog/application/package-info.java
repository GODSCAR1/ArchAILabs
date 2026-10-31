/**
 * Aplicación del catálogo: casos de uso que orquestan el dominio a través de sus puertos.
 *
 * <p>Depende solo de {@code catalog.domain}; NUNCA de {@code catalog.infrastructure} (regla que
 * ArchUnit verificará desde S17; aún no activo). Todo método público de un caso de uso tiene test
 * unitario.
 */
package com.codeja.master_acdc.catalog.application;
