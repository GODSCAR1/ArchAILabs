package com.codeja.master_acdc.identity;

import com.codeja.master_acdc.identity.domain.model.UserAccount;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
abstract class AdminUsersSupport extends IdentityIntegrationSupport {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired AccessTokenIssuer issuer;
  @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
  @Autowired org.springframework.security.oauth2.jwt.JwtDecoder decoder;

  @BeforeEach
  void seed_120_accounts() {
    redis.delete(
        IntStream.range(0, 120)
            .mapToObj(
                index ->
                    com.codeja.master_acdc.identity.infrastructure.adapters.out.redis
                            .RedisSessionVersions.PREFIX
                        + idFor(index))
            .toList());
    jdbc.update("delete from refresh_token");
    jdbc.update("delete from password_reset_token"); // lab S13: FK a user_account (V9)
    jdbc.update("delete from user_account");
    var rows =
        IntStream.range(0, 120)
            .mapToObj(
                index ->
                    new Object[] {
                      idFor(index),
                      "admin-fixture-" + index + "@example.com",
                      index == 0 ? "{ADMIN}" : "{CUSTOMER,SUPPORT}",
                      index != 119
                    })
            .toList();
    jdbc.batchUpdate(
        "insert into user_account(id,email,password_hash,roles,enabled,session_version)"
            + " values (?,?,'unused-fixture-hash',cast(? as text[]),?,0)",
        rows);
  }

  String bearer(String role) {
    String token =
        issuer
            .issue(
                new UserAccount(
                    idFor(0),
                    "admin-fixture-0@example.com",
                    "unused-fixture-hash",
                    new UserAccount.Profile(
                        Set.of(role),
                        true,
                        jdbc.queryForObject(
                            "select session_version from user_account where id=?",
                            Long.class,
                            idFor(0)),
                        null)))
            .value();
    decoder.decode(token);
    return "Bearer " + token;
  }

  UUID idFor(int index) {
    return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(index + 1));
  }
}
