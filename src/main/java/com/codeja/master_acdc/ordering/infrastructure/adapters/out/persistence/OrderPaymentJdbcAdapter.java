package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.exception.OrderNotConfirmableException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.PaymentAttempt;
import com.codeja.master_acdc.ordering.domain.port.OrderPayments;
import com.codeja.master_acdc.ordering.domain.port.PublishOrderEvents;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public final class OrderPaymentJdbcAdapter implements OrderPayments {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;
  private final PublishOrderEvents events;

  public OrderPaymentJdbcAdapter(
      JdbcTemplate jdbc, TransactionTemplate transaction, PublishOrderEvents events) {
    this.jdbc = jdbc;
    this.transaction = transaction;
    this.events = events;
  }

  @Override
  public Optional<BigDecimal> fixedAmount(UUID orderId, UUID customerId) {
    return transaction.execute(
        status ->
            jdbc.query(
                "select payment_amount from orders where id = ? and customer_id = ?",
                rs -> rs.next() ? Optional.ofNullable(rs.getBigDecimal(1)) : Optional.empty(),
                orderId,
                customerId));
  }

  @Override
  public PaymentAttempt startAttempt(UUID orderId, UUID customerId, BigDecimal amount) {
    return transaction.execute(status -> start(orderId, customerId, amount));
  }

  private PaymentAttempt start(UUID orderId, UUID customerId, BigDecimal amount) {
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
      throw new OrderNotConfirmableException(orderId);
    }
    return jdbc.query(
            "select payment_amount, payment_attempt from orders where id = ? and customer_id = ?",
            rs ->
                rs.next()
                    ? Optional.of(new PaymentAttempt(rs.getBigDecimal(1), rs.getInt(2)))
                    : Optional.<PaymentAttempt>empty(),
            orderId,
            customerId)
        .orElseThrow(() -> new OrderNotConfirmableException(orderId));
  }

  @Override
  public void abandonAttempt(UUID orderId, UUID customerId, boolean declined) {
    transaction.executeWithoutResult(
        status -> {
          int updated =
              jdbc.update(
                  """
                  update orders set status = 'PENDING', payment_attempt = payment_attempt + ?
                  where id = ? and customer_id = ? and status = 'PAYING'
                  """,
                  declined ? 1 : 0,
                  orderId,
                  customerId);
          if (updated != 1) {
            throw new OrderNotConfirmableException(orderId);
          }
        });
  }

  @Override
  public void settle(Order order) {
    transaction.executeWithoutResult(
        status -> {
          int updated =
              jdbc.update(
                  """
                  update orders set status = 'PAID'
                  where id = ? and customer_id = ? and status = 'PAYING'
                  """,
                  order.id(),
                  order.customerId());
          if (updated != 1) {
            throw new OrderNotConfirmableException(order.id());
          }
          var pending = order.pullEvents();
          if (!pending.isEmpty()) {
            events.publish(pending);
          }
        });
  }
}
