package com.codeja.master_acdc;

import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real signed bearer for integration fixtures that write catalog or reserve stock. */
public final class CatalogManagerToken {
  private CatalogManagerToken() {}

  public static String issue(JdbcTemplate jdbc, AccessTokenIssuer issuer) {
    return OrderBearer.issue(jdbc, issuer, null, "CATALOG_MANAGER");
  }
}
