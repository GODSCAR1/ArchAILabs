/**
 * Aplicación: casos de uso que orquestan el dominio a través de sus puertos.
 *
 * <p>Depende solo de {@code domain}; NUNCA de {@code infrastructure} (regla que ArchUnit verificará
 * desde S17; aún no activo). Todo método público de un caso de uso tiene test unitario.
 */
package com.codeja.master_acdc.application;
