/**
 * Modelo de dominio: entidades, agregados y value objects.
 *
 * <p>Java puro: NUNCA importa Spring, JPA ni Jackson. Los value objects son {@code record}
 * inmutables y validan sus invariantes en el constructor. Regla que ArchUnit verificará desde S17;
 * aún no activo.
 */
package com.codeja.master_acdc.domain.model;
