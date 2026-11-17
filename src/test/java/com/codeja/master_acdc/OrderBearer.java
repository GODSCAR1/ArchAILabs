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
    saveAccount(jdbc, account, customer, roles);
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

  /**
   * Lab S16: dos hilos pueden emitir el mismo bearer a la vez (cancelaciones concurrentes). Con "on
   * conflict(id) do update", el segundo INSERT concurrente puede chocar con la UNIQUE del email
   * antes de que PostgreSQL resuelva el conflicto por id. "do nothing" sin destino cubre cualquier
   * UNIQUE y el UPDATE posterior fija roles y cliente.
   */
  private static void saveAccount(JdbcTemplate jdbc, UUID account, UUID customer, String... roles) {
    String roleArray = "{" + String.join(",", roles) + "}";
    jdbc.update(
        """
        insert into user_account(id,email,password_hash,roles,enabled,session_version,customer_id)
        values (?,?,'unused',cast(? as text[]),true,0,?) on conflict do nothing
        """,
        account,
        account + "@example.com",
        roleArray,
        customer);
    jdbc.update(
        "update user_account set roles = cast(? as text[]), customer_id = ? where id = ?",
        roleArray,
        customer,
        account);
  }
}
