package com.codeja.master_acdc.ordering.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.ordering.application.port.CancelOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.CreateOrderUseCase;
import com.codeja.master_acdc.ordering.domain.exception.InsufficientStockException;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class OrderTransactionIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static final String CUSTOMER = "550e8400-e29b-41d4-a716-446655440000";
  private static final String A_TEXT = "11111111-1111-1111-1111-111111111111";
  private static final String B_TEXT = "22222222-2222-2222-2222-222222222222";

  @Autowired CreateOrderUseCase createOrderUseCase;
  @Autowired CancelOrderUseCase cancelOrderUseCase;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A_TEXT + "', 10)",
        "insert into stock (product_id, available) values ('" + B_TEXT + "', 1)"
      })
  void should_rollback_order_and_stock_when_reservation_fails() {
    assertThatThrownBy(
            () ->
                createOrderUseCase.create(
                    UUID.fromString(CUSTOMER), List.of(line(A_TEXT, 3), line(B_TEXT, 2))))
        .isInstanceOf(InsufficientStockException.class);

    assertThat(rowCount("orders")).isZero();
    assertThat(rowCount("order_lines")).isZero();
    assertThat(availableOf(A_TEXT)).isEqualTo(10);
    assertThat(availableOf(B_TEXT)).isEqualTo(1);
  }

  @Test
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A_TEXT + "', 5)",
        "insert into orders (id, customer_id, status) values ('550e8400-e29b-41d4-a716-446655440001', '"
            + CUSTOMER
            + "', 'PENDING')",
        "insert into order_lines (order_id, product_id, quantity) values ('550e8400-e29b-41d4-a716-446655440001', '"
            + A_TEXT
            + "', 3)"
      })
  void should_rollback_order_and_stock_when_release_fails() {
    jdbcTemplate.update("delete from stock where product_id = ?", UUID.fromString(A_TEXT));

    assertThatThrownBy(
            () ->
                cancelOrderUseCase.cancel(
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                    UUID.fromString(CUSTOMER)))
        .isInstanceOf(
            com.codeja.master_acdc.inventory.domain.exception.StockRecordNotFoundException.class);

    assertThat(rowCount("orders")).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from orders where id = ?",
                String.class,
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001")))
        .isEqualTo("PENDING");
  }

  private static OrderLine line(String productId, int quantity) {
    return new OrderLine(UUID.fromString(productId), quantity);
  }

  private int rowCount(String table) {
    return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
  }

  private int availableOf(String productId) {
    return jdbcTemplate.queryForObject(
        "select available from stock where product_id = ?",
        Integer.class,
        UUID.fromString(productId));
  }
}
