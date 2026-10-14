/**
 * Modelo de dominio: entidades, agregados y value objects de la tienda (Product, Order, Money,
 * ProductId...).
 *
 * <p>Java puro: NUNCA importa Spring, JPA ni Jackson. Los value objects son {@code record}
 * inmutables y validan sus invariantes en el constructor. Verificado con ArchUnit desde la Sección
 * 17.
 */
package com.codeja.master_acdc.domain.model;
