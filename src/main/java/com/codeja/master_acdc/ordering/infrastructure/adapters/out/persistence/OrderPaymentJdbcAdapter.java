package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.PaymentAttempt;
import com.codeja.master_acdc.ordering.domain.port.OrderPaymentRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class OrderPaymentJdbcAdapter implements OrderPaymentRepository {
  private final JdbcTemplate jdbc;

  public OrderPaymentJdbcAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<BigDecimal> findAmount(UUID orderId, UUID customerId) {
    return jdbc.query(
        "select payment_amount from orders where id = ? and customer_id = ?",
        rs -> rs.next() ? Optional.ofNullable(rs.getBigDecimal(1)) : Optional.empty(),
        orderId,
        customerId);
  }

  @Override
  public Optional<PaymentAttempt> start(UUID orderId, UUID customerId, BigDecimal amount) {
    int updated =
        jdbc.update(
            """
            update orders set status = 'PAYING', payment_amount = coalesce(payment_amount, ?)
            where id = ? and customer_id = ? and status = 'PENDING'
            """,
            amount,
            orderId,
            customerId);
    if (updated == 0) {
      return Optional.empty();
    }
    return jdbc.query(
        "select payment_amount, payment_attempt from orders where id = ? and customer_id = ?",
        rs ->
            rs.next()
                ? Optional.of(new PaymentAttempt(rs.getBigDecimal(1), rs.getInt(2)))
                : Optional.empty(),
        orderId,
        customerId);
  }

  @Override
  public boolean returnToPending(UUID orderId, UUID customerId, boolean declined) {
    return jdbc.update(
            """
            update orders set status = 'PENDING', payment_attempt = payment_attempt + ?
            where id = ? and customer_id = ? and status = 'PAYING'
            """,
            declined ? 1 : 0,
            orderId,
            customerId)
        == 1;
  }

  @Override
  public boolean markPaid(UUID orderId, UUID customerId) {
    return jdbc.update(
            """
            update orders set status = 'PAID'
            where id = ? and customer_id = ? and status = 'PAYING'
            """,
            orderId,
            customerId)
        == 1;
  }
}
