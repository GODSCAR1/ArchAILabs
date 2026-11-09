package com.codeja.master_acdc;

import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class OrderBearer {
  private OrderBearer() {}

  public static String issue(
      JdbcTemplate jdbc, AccessTokenIssuer issuer, UUID customer, String... roles) {
    UUID account =
        customer == null
            ? UUID.randomUUID()
            : UUID.nameUUIDFromBytes(
                ("order-account-" + customer).getBytes(StandardCharsets.UTF_8));
    if (customer != null) {
      jdbc.update(
          "insert into customers(id,name) values (?, 'Order fixture') on conflict(id) do nothing",
          customer);
    }
    jdbc.update(
        "insert into user_account(id,email,password_hash,roles,enabled,session_version,customer_id)"
            + " values (?,?,'unused',cast(? as text[]),true,0,?) on conflict(id) do update set"
            + " roles=excluded.roles,customer_id=excluded.customer_id",
        account,
        account + "@example.com",
        "{" + String.join(",", roles) + "}",
        customer);
    return issuer
        .issue(
            new UserAccount(
                account,
                account + "@example.com",
                "unused",
                new UserAccount.Profile(
                    Set.of(roles),
                    true,
                    jdbc.queryForObject(
                        "select session_version from user_account where id=?", Long.class, account),
                    customer)))
        .value();
  }
}
