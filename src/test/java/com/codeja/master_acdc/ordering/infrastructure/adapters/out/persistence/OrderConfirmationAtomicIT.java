package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.port.PaymentTransactions;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class OrderConfirmationAtomicIT extends IntegrationTest {
  private final UUID id = UUID.randomUUID();
  private final UUID customer = UUID.randomUUID();
  private final Order order =
      Order.restore(id, customer, OrderStatus.PAYING, List.of(new OrderLine(UUID.randomUUID(), 1)));
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PaymentTransactions transactions;

  @BeforeEach
  void insert_order() {
    jdbc.update(
        "insert into orders (id, customer_id, status) values (?, ?, 'PAYING')", id, customer);
  }

  @Test
  @DisplayName("La confirmación y el evento se registran juntos")
  void should_save_paid_and_outbox_when_payment_is_authorized() {
    assertThat(transactions.complete(order).status()).isEqualTo(OrderStatus.PAID);
    assertThat(status()).isEqualTo("PAID");
    assertThat(eventCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("Fallo al registrar confirmación revierte ambos cambios")
  void should_keep_paying_when_outbox_insertion_fails() {
    doThrow(new DataIntegrityViolationException("test failure"))
        .when(spiedConfirmations)
        .append(any());

    assertThatThrownBy(() -> transactions.complete(order))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(status()).isEqualTo("PAYING");
    assertThat(eventCount()).isZero();
  }

  private String status() {
    return jdbc.queryForObject("select status from orders where id = ?", String.class, id);
  }

  private int eventCount() {
    return jdbc.queryForObject(
        "select count(*) from order_confirmation_outbox where order_id = ?", Integer.class, id);
  }
}
