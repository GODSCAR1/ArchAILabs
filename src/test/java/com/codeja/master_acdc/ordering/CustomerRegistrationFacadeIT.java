package com.codeja.master_acdc.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.identity.IdentityIntegrationSupport;
import com.codeja.master_acdc.ordering.api.CustomerRegistrationFacade;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class CustomerRegistrationFacadeIT extends IdentityIntegrationSupport {
  @Autowired private CustomerRegistrationFacade facade;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager manager;

  @Test
  void should_persist_customer_when_public_facade_registers() {
    UUID id = facade.register("identity-facade");
    assertThat(jdbc.queryForObject("select name from customers where id=?", String.class, id))
        .isEqualTo("identity-facade");
  }

  @Test
  void should_join_caller_transaction_when_registration_rolls_back() {
    long before = jdbc.queryForObject("select count(*) from customers", Long.class);
    new TransactionTemplate(manager)
        .executeWithoutResult(
            status -> {
              facade.register("identity-rollback-facade");
              status.setRollbackOnly();
            });
    assertThat(jdbc.queryForObject("select count(*) from customers", Long.class)).isEqualTo(before);
    assertThatThrownBy(() -> facade.register(" ")).isInstanceOf(IllegalArgumentException.class);
  }
}
