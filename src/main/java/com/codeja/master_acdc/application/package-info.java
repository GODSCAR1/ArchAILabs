/**
 * Aplicación: casos de uso que orquestan el dominio a través de sus puertos.
 *
 * <p>Depende solo de {@code domain}; NUNCA de {@code infrastructure}. Inyección SIEMPRE por
 * constructor. Todo método público de un caso de uso tiene test unitario ({@code
 * should_<comportamiento>_when_<condición>}). Verificado con ArchUnit desde la Sección 17.
 */
package com.codeja.master_acdc.application;
