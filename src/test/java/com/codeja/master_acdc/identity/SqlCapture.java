package com.codeja.master_acdc.identity;

import java.util.ArrayList;
import java.util.List;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Captura el SQL que Hibernate prepara en el hilo actual, solo entre {@link #start()} y {@link
 * #stop()}. Registrado para todos los tests de {@link IdentityIntegrationSupport}; fuera de una
 * captura no guarda nada. MockMvc ejecuta la petición en el hilo del test, así que una captura
 * abierta antes de {@code mvc.perform(...)} ve el SQL del endpoint.
 */
public final class SqlCapture implements StatementInspector {
  private static final ThreadLocal<List<String>> STATEMENTS = new ThreadLocal<>();

  /** Empieza (o reinicia) la captura en el hilo actual y devuelve la lista viva. */
  public static List<String> start() {
    List<String> statements = new ArrayList<>();
    STATEMENTS.set(statements);
    return statements;
  }

  /** SQL capturado desde el último {@link #start()}; vacío si no hay captura abierta. */
  public static List<String> statements() {
    List<String> statements = STATEMENTS.get();
    return statements == null ? List.of() : List.copyOf(statements);
  }

  /** Cierra la captura del hilo actual (la base lo hace tras cada test). */
  public static void stop() {
    STATEMENTS.remove();
  }

  @Override
  public String inspect(String sql) {
    List<String> statements = STATEMENTS.get();
    if (statements != null) {
      statements.add(sql);
    }
    return sql;
  }
}
