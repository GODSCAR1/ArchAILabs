/**
 * Infraestructura del catálogo: todo lo que conecta la aplicación con el mundo exterior
 * (frameworks, base de datos, HTTP, mensajería, configuración).
 *
 * <p>Es la única capa donde se permiten Spring y JPA. Depende de {@code catalog.application} y
 * {@code catalog.domain}; ninguna otra capa depende de ella.
 */
package com.codeja.master_acdc.catalog.infrastructure;
