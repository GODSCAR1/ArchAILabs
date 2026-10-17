package com.codeja.master_acdc.ordering;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderPlacementIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static final String TYPE_BASE = "https://api.master-acdc.dev/problems/";
  private static final String CUSTOMER = "550e8400-e29b-41d4-a716-446655440000";
  private static final String A = "11111111-1111-1111-1111-111111111111";
  private static final String B = "22222222-2222-2222-2222-222222222222";
  private static final String X = "99999999-9999-9999-9999-999999999999";

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("Pedido creado con stock disponible")
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A + "', 10)",
        "insert into stock (product_id, available) values ('" + B + "', 5)"
      })
  void should_create_pending_order_and_reserve_stock_when_lines_have_stock() throws Exception {
    placeOrder(CUSTOMER, lines(line(A, "3"), line(B, "2")))
        .andExpect(status().isCreated())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(jsonPath("$.customerId").value(CUSTOMER))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.lines.length()").value(2));

    assertThat(orderCount()).isEqualTo(1);
    assertThat(lineCount()).isEqualTo(2);
    assertThat(quantityOf()).isEqualTo(3);
    assertThat(availableOf(A)).isEqualTo(7);
    assertThat(availableOf(B)).isEqualTo(3);
  }

  @Test
  @DisplayName("Frontera: cantidad igual al stock disponible")
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A + "', 10)"
      })
  void should_create_order_and_leave_zero_stock_when_quantity_equals_stock() throws Exception {
    placeOrder(CUSTOMER, lines(line(A, "10"))).andExpect(status().isCreated());

    assertThat(orderCount()).isEqualTo(1);
    assertThat(availableOf(A)).isZero();
  }

  @Test
  @DisplayName("Frontera: cantidad una unidad por encima del stock")
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A + "', 10)"
      })
  void should_reject_and_keep_stock_when_quantity_exceeds_by_one() throws Exception {
    placeOrder(CUSTOMER, lines(line(A, "11")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "insufficient-stock"));

    assertThat(orderCount()).isZero();
    assertThat(availableOf(A)).isEqualTo(10);
  }

  @Test
  @DisplayName("Sin cabecera X-Customer-Id")
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A + "', 10)"
      })
  void should_reject_400_missing_customer_id_when_header_absent() throws Exception {
    placeOrder(null, lines(line(A, "3")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "missing-customer-id"));

    assertThat(orderCount()).isZero();
    assertThat(availableOf(A)).isEqualTo(10);
  }

  @Test
  @DisplayName("X-Customer-Id que no es UUID")
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A + "', 10)"
      })
  void should_reject_400_invalid_customer_id_when_not_uuid() throws Exception {
    placeOrder("no-es-uuid", lines(line(A, "3")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "invalid-customer-id"));

    assertThat(orderCount()).isZero();
    assertThat(availableOf(A)).isEqualTo(10);
  }

  @Test
  @DisplayName("Pedido sin líneas")
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A + "', 10)"
      })
  void should_reject_422_empty_order_when_no_lines() throws Exception {
    placeOrder(CUSTOMER, lines())
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "empty-order"));

    assertThat(orderCount()).isZero();
    assertThat(availableOf(A)).isEqualTo(10);
  }

  @Test
  @DisplayName("Producto repetido en dos líneas")
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A + "', 10)"
      })
  void should_reject_422_duplicate_product_when_product_repeated() throws Exception {
    placeOrder(CUSTOMER, lines(line(A, "2"), line(A, "3")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "duplicate-product"));

    assertThat(orderCount()).isZero();
    assertThat(availableOf(A)).isEqualTo(10);
  }

  @Test
  @DisplayName("Cantidad cero o negativa")
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A + "', 10)",
        "insert into stock (product_id, available) values ('" + B + "', 5)"
      })
  void should_reject_422_invalid_quantity_when_quantity_not_positive() throws Exception {
    placeOrder(CUSTOMER, lines(line(A, "0"), line(B, "2")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "invalid-quantity"));

    assertThat(orderCount()).isZero();
    assertThat(availableOf(A)).isEqualTo(10);
    assertThat(availableOf(B)).isEqualTo(5);
  }

  @Test
  @DisplayName("Línea sin producto")
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A + "', 10)"
      })
  void should_reject_422_invalid_line_when_product_missing() throws Exception {
    placeOrder(CUSTOMER, "{\"lines\":[{\"quantity\":2}," + line(A, "1") + "]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "invalid-line"));

    assertThat(orderCount()).isZero();
    assertThat(availableOf(A)).isEqualTo(10);
  }

  @Test
  @DisplayName("Pedido con producto desconocido")
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A + "', 10)"
      })
  void should_reject_422_unknown_product_when_product_not_in_inventory() throws Exception {
    placeOrder(CUSTOMER, lines(line(A, "3"), line(X, "1")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "unknown-product"));

    assertThat(orderCount()).isZero();
    assertThat(availableOf(A)).isEqualTo(10);
  }

  @Test
  @DisplayName("Una línea sin stock suficiente")
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A + "', 10)",
        "insert into stock (product_id, available) values ('" + B + "', 1)"
      })
  void should_reject_409_and_keep_all_stock_when_one_line_lacks_stock() throws Exception {
    placeOrder(CUSTOMER, lines(line(A, "3"), line(B, "2")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "insufficient-stock"));

    assertThat(orderCount()).isZero();
    assertThat(availableOf(A)).isEqualTo(10);
    assertThat(availableOf(B)).isEqualTo(1);
  }

  @Test
  @DisplayName("Precedencia: error de cabecera antes que cuerpo y stock")
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A + "', 10)",
        "insert into stock (product_id, available) values ('" + B + "', 1)"
      })
  void should_reject_400_header_precedence_when_header_body_and_stock_errors() throws Exception {
    placeOrder(null, lines(line(A, "0"), line(B, "2")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "missing-customer-id"));

    assertThat(orderCount()).isZero();
    assertThat(availableOf(A)).isEqualTo(10);
    assertThat(availableOf(B)).isEqualTo(1);
  }

  @Test
  @DisplayName("Precedencia: error de cuerpo antes que stock")
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A + "', 10)",
        "insert into stock (product_id, available) values ('" + B + "', 1)"
      })
  void should_reject_422_body_precedence_when_body_and_stock_errors() throws Exception {
    placeOrder(CUSTOMER, lines(line(A, "0"), line(B, "2")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "invalid-quantity"));

    assertThat(orderCount()).isZero();
    assertThat(availableOf(A)).isEqualTo(10);
    assertThat(availableOf(B)).isEqualTo(1);
  }

  @Test
  @DisplayName("Precedencia: producto desconocido antes que stock insuficiente")
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + B + "', 1)"
      })
  void should_reject_422_unknown_product_when_other_line_lacks_stock() throws Exception {
    placeOrder(CUSTOMER, lines(line(B, "2"), line(X, "1")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "unknown-product"));

    assertThat(orderCount()).isZero();
    assertThat(availableOf(B)).isEqualTo(1);
  }

  @Test
  @DisplayName("Dos pedidos concurrentes por la última unidad")
  @Sql(
      statements = {
        "delete from order_lines",
        "delete from orders",
        "delete from stock",
        "insert into stock (product_id, available) values ('" + A + "', 1)"
      })
  void should_create_exactly_one_order_when_two_concurrent_orders_contend_last_unit()
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<Integer> attempt =
        () -> {
          start.await();
          return placeOrder(CUSTOMER, lines(line(A, "1"))).andReturn().getResponse().getStatus();
        };

    try {
      Future<Integer> first = executor.submit(attempt);
      Future<Integer> second = executor.submit(attempt);
      start.countDown();

      List<Integer> statuses = List.of(first.get(30, SECONDS), second.get(30, SECONDS));

      assertThat(statuses).containsExactlyInAnyOrder(201, 409);
      assertThat(orderCount()).isEqualTo(1);
      assertThat(availableOf(A)).isZero();
    } finally {
      executor.shutdownNow();
    }
  }

  private ResultActions placeOrder(String customerId, String body) throws Exception {
    MockHttpServletRequestBuilder request =
        post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body);
    if (customerId != null) {
      request.header("X-Customer-Id", customerId);
    }
    return mockMvc.perform(request);
  }

  private static String line(String productId, String quantity) {
    return "{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}";
  }

  private static String lines(String... lines) {
    return "{\"lines\":[" + String.join(",", lines) + "]}";
  }

  private int orderCount() {
    return jdbcTemplate.queryForObject("select count(*) from orders", Integer.class);
  }

  private int lineCount() {
    return jdbcTemplate.queryForObject("select count(*) from order_lines", Integer.class);
  }

  private int quantityOf() {
    return jdbcTemplate.queryForObject("select quantity from order_lines limit 1", Integer.class);
  }

  private int availableOf(String productId) {
    return jdbcTemplate.queryForObject(
        "select available from stock where product_id = ?",
        Integer.class,
        UUID.fromString(productId));
  }
}
