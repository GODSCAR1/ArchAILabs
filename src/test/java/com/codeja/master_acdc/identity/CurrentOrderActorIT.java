package com.codeja.master_acdc.identity;

import static com.codeja.master_acdc.AuthorizedCalls.withAuthorities;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.ordering.api.CurrentOrderActor;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentOrderActorIT extends SessionIntegrationSupport {
  @Autowired private CurrentOrderActor actor;
  @Autowired private org.springframework.security.oauth2.jwt.JwtDecoder decoder;

  @Test
  void should_distinguish_missing_unlinked_and_linked_accounts_when_resolving_actor() {
    var account = registered();
    withSubject(UUID.randomUUID().toString(), () -> assertThat(actor.current()).isEmpty());
    withSubject("invalid-subject", () -> assertThat(actor.current()).isEmpty());
    withSubject(
        account.id().toString(),
        () -> {
          assertThat(actor.current().orElseThrow().customerId())
              .isEqualTo(account.profile().customerId());
          jdbc.update("update user_account set customer_id=null where id=?", account.id());
          assertThat(actor.current())
              .hasValueSatisfying(value -> assertThat(value.customerId()).isNull());
        });
  }

  @Test
  @DisplayName("Cambio de asociación se observa con el mismo token")
  void should_observe_current_association_when_same_authentication_is_reused() {
    var first = registered();
    var second = registered();
    var bearer = login(first).accessToken().value();
    jdbc.update("update user_account set customer_id=null where id=?", second.id());
    withSubject(
        first.id().toString(),
        () -> {
          SecurityContextHolder.getContext()
              .setAuthentication(
                  new org.springframework.security.oauth2.server.resource.authentication
                      .JwtAuthenticationToken(
                      decoder.decode(bearer),
                      java.util.List.of(
                          new org.springframework.security.core.authority.SimpleGrantedAuthority(
                              "order:read-own"))));
          var authentication = SecurityContextHolder.getContext().getAuthentication();
          assertThat(actor.current().orElseThrow().customerId())
              .isEqualTo(first.profile().customerId());
          jdbc.update(
              "update user_account set customer_id=? where id=?",
              second.profile().customerId(),
              first.id());
          assertThat(actor.current().orElseThrow().customerId())
              .isEqualTo(second.profile().customerId());
          assertThat(SecurityContextHolder.getContext().getAuthentication())
              .isSameAs(authentication);
        });
  }

  private void withSubject(String subject, Runnable operation) {
    withAuthorities(
        () -> {
          SecurityContextHolder.getContext()
              .setAuthentication(new TestingAuthenticationToken(subject, null, "order:read-own"));
          operation.run();
          return null;
        },
        "order:read-own");
  }
}
