package com.codeja.master_acdc;

import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import java.util.Set;
import java.util.UUID;

/** Real signed bearer for integration fixtures that write catalog or reserve stock. */
public final class CatalogManagerToken {
  private CatalogManagerToken() {}

  public static String issue(AccessTokenIssuer issuer) {
    return issuer
        .issue(
            new UserAccount(
                UUID.randomUUID(),
                "catalog-fixture@example.com",
                "unused",
                new UserAccount.Profile(Set.of("CATALOG_MANAGER"), true, 0, null)))
        .value();
  }
}
