/**
 * Adaptador de salida de persistencia: entidad JPA, repositorio Spring Data y adaptador que
 * implementa el puerto {@code ProductRepository}.
 *
 * <p>El esquema lo manda Flyway; el adaptador traduce la violación de unicidad a la excepción de
 * dominio.
 */
package com.codeja.master_acdc.infrastructure.adapters.out.persistence;
