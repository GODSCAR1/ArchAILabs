package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.ordering.domain.model.PaymentAttempt;
import com.codeja.master_acdc.ordering.domain.port.OrderPaymentRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class OrderPaymentJdbcAdapterIT extends IntegrationTest {
  private final UUID orderId = UUID.randomUUID();
  private final UUID customerId = UUID.randomUUID();
  @Autowired private JdbcTemplate jdbc;
  @Autowired private OrderPaymentRepository payments;
  @Autowired private org.springframework.transaction.PlatformTransactionManager manager;

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
    var tx = new TransactionTemplate(manager);
    var first = tx.execute(status -> payments.start(orderId, customerId, new BigDecimal("100")));
    assertThat(first).contains(new PaymentAttempt(new BigDecimal("100.00"), 0));
    var returned = tx.execute(status -> payments.returnToPending(orderId, customerId, false));
    assertThat(returned).isTrue();
    var retry = tx.execute(status -> payments.start(orderId, customerId, new BigDecimal("200")));
    assertThat(retry).contains(new PaymentAttempt(new BigDecimal("100.00"), 0));
    assertThat(payments.findAmount(orderId, customerId)).contains(new BigDecimal("100.00"));
    assertThat(payments.findAmount(orderId, UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("Un rechazo definitivo incrementa el intento sin cambiar el importe")
  void should_increment_attempt_when_payment_is_declined() {
    var tx = new TransactionTemplate(manager);
    var first = tx.execute(status -> payments.start(orderId, customerId, new BigDecimal("100")));
    var returned = tx.execute(status -> payments.returnToPending(orderId, customerId, true));
    var retry = tx.execute(status -> payments.start(orderId, customerId, new BigDecimal("200")));
    assertThat(first).isPresent();
    assertThat(returned).isTrue();
    assertThat(retry).contains(new PaymentAttempt(new BigDecimal("100.00"), 1));
  }

  @Test
  @DisplayName("Solo el propietario puede iniciar un pago pendiente")
  void should_refuse_start_when_order_is_foreign_or_not_pending() {
    var tx = new TransactionTemplate(manager);
    var foreign = tx.execute(status -> payments.start(orderId, UUID.randomUUID(), BigDecimal.ONE));
    var first = tx.execute(status -> payments.start(orderId, customerId, BigDecimal.ONE));
    var second = tx.execute(status -> payments.start(orderId, customerId, BigDecimal.ONE));
    assertThat(foreign).isEmpty();
    assertThat(first).isPresent();
    assertThat(second).isEmpty();
  }
}
