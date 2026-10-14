/**
 * Configuración de Spring: clases {@code @Configuration} que ensamblan los casos de uso y los
 * adaptadores con métodos {@code @Bean}.
 *
 * <p>Único lugar donde se permite {@code ApplicationContext}. Sin lógica de negocio en los métodos
 * {@code @Bean}. Secretos solo por variables de entorno ({@code ${VARIABLE}}), nunca en archivos.
 */
package com.codeja.master_acdc.infrastructure.config;
