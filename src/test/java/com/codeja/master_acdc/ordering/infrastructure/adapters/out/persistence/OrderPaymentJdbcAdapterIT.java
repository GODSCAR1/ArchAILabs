package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotConfirmableException;
import com.codeja.master_acdc.ordering.domain.model.PaymentAttempt;
import com.codeja.master_acdc.ordering.domain.port.OrderPayments;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class OrderPaymentJdbcAdapterIT extends IntegrationTest {
  private final UUID orderId = UUID.randomUUID();
  private final UUID customerId = UUID.randomUUID();
  @Autowired private JdbcTemplate jdbc;
  @Autowired private OrderPayments payments;

  @BeforeEach
  void insert_order() {
    jdbc.update(
        "insert into orders (id, customer_id, status) values (?, ?, 'PENDING')",
        orderId,
        customerId);
  }

  @Test
  @DisplayName("El importe y el intento sobreviven a un error ambiguo")
  void should_keep_amount_and_attempt_when_payment_fails_ambiguously() {
    var first = payments.startAttempt(orderId, customerId, new BigDecimal("100"));
    assertThat(first).isEqualTo(new PaymentAttempt(new BigDecimal("100.00"), 0));
    payments.abandonAttempt(orderId, customerId, false);
    assertThat(jdbc.queryForObject("select status from orders where id = ?", String.class, orderId))
        .isEqualTo("PENDING");
    var retry = payments.startAttempt(orderId, customerId, new BigDecimal("200"));
    assertThat(retry).isEqualTo(new PaymentAttempt(new BigDecimal("100.00"), 0));
    assertThat(payments.fixedAmount(orderId, customerId)).contains(new BigDecimal("100.00"));
    assertThat(payments.fixedAmount(orderId, UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("Un rechazo definitivo incrementa el intento sin cambiar el importe")
  void should_increment_attempt_when_payment_is_declined() {
    var first = payments.startAttempt(orderId, customerId, new BigDecimal("100"));
    payments.abandonAttempt(orderId, customerId, true);
    assertThat(jdbc.queryForObject("select status from orders where id = ?", String.class, orderId))
        .isEqualTo("PENDING");
    var retry = payments.startAttempt(orderId, customerId, new BigDecimal("200"));
    assertThat(first).isEqualTo(new PaymentAttempt(new BigDecimal("100.00"), 0));
    assertThat(retry).isEqualTo(new PaymentAttempt(new BigDecimal("100.00"), 1));
  }

  @Test
  @DisplayName("Solo el propietario puede iniciar un pago pendiente")
  void should_refuse_start_when_order_is_foreign_or_not_pending() {
    assertThatThrownBy(() -> payments.startAttempt(orderId, UUID.randomUUID(), BigDecimal.ONE))
        .isInstanceOf(OrderNotConfirmableException.class);
    var first = payments.startAttempt(orderId, customerId, BigDecimal.ONE);
    assertThat(first).isEqualTo(new PaymentAttempt(new BigDecimal("1.00"), 0));
    assertThatThrownBy(() -> payments.startAttempt(orderId, customerId, BigDecimal.ONE))
        .isInstanceOf(OrderNotConfirmableException.class);
  }
}
