package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
class OrderRepositoryAdapterIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static final UUID CUSTOMER = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
  private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired OrderRepositoryAdapter adapter;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void should_persist_pending_order_with_lines_when_saved() {
    Order order = Order.create(CUSTOMER, List.of(new OrderLine(A, 3), new OrderLine(B, 2)));

    adapter.save(order);

    assertThat(
            jdbcTemplate.queryForObject(
                "select status from orders where id = ?", String.class, order.id()))
        .isEqualTo("PENDING");
    assertThat(
            jdbcTemplate.queryForObject(
                "select customer_id from orders where id = ?", UUID.class, order.id()))
        .isEqualTo(CUSTOMER);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from order_lines where order_id = ?", Integer.class, order.id()))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "select quantity from order_lines where order_id = ? and product_id = ?",
                Integer.class,
                order.id(),
                A))
        .isEqualTo(3);
  }

  @Test
  void should_find_order_by_id_and_customer_id_when_owned() {
    Order order = Order.create(CUSTOMER, List.of(new OrderLine(A, 3), new OrderLine(B, 2)));
    adapter.save(order);

    var found = adapter.findByIdAndCustomerId(order.id(), CUSTOMER);

    assertThat(found).contains(order);
  }

  @Test
  void should_return_empty_when_order_belongs_to_another_customer() {
    Order order = Order.create(CUSTOMER, List.of(new OrderLine(A, 3)));
    adapter.save(order);
    UUID otherCustomer = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");

    assertThat(adapter.findByIdAndCustomerId(order.id(), otherCustomer)).isEmpty();
  }

  @Test
  void should_cancel_order_when_status_is_pending() {
    Order order = Order.create(CUSTOMER, List.of(new OrderLine(A, 3)));
    adapter.save(order);

    boolean cancelled = adapter.cancelIfCancelable(order.id(), CUSTOMER);

    assertThat(cancelled).isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from orders where id = ?", String.class, order.id()))
        .isEqualTo("CANCELLED");
  }

  @Test
  void should_not_cancel_order_when_status_is_shipped() {
    Order order = Order.create(CUSTOMER, List.of(new OrderLine(A, 3)));
    adapter.save(order);
    jdbcTemplate.update("update orders set status = 'SHIPPED' where id = ?", order.id());

    boolean cancelled = adapter.cancelIfCancelable(order.id(), CUSTOMER);

    assertThat(cancelled).isFalse();
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from orders where id = ?", String.class, order.id()))
        .isEqualTo("SHIPPED");
  }
}
