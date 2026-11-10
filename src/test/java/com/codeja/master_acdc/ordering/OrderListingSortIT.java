package com.codeja.master_acdc.ordering;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.OrderBearer;
import com.codeja.master_acdc.identity.IdentityIntegrationSupport;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OrderListingSortIT extends IdentityIntegrationSupport {
  private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mvc;
  @Autowired private AccessTokenIssuer issuer;
  private String token;

  @BeforeEach
  void seed_orders() {
    jdbc.update("delete from order_lines");
    jdbc.update("delete from orders");
    UUID customer = UUID.randomUUID();
    jdbc.update("insert into customers(id,name) values (?, ?)", customer, "Ada");
    jdbc.update("insert into orders(id,customer_id,status) values (?, ?, 'PAID')", FIRST, customer);
    jdbc.update(
        "insert into orders(id,customer_id,status) values (?, ?, 'PAID')", SECOND, customer);
    UUID product = UUID.randomUUID();
    jdbc.update(
        "insert into order_lines(order_id,product_id,quantity) values (?, ?, ?)",
        FIRST,
        product,
        2);
    jdbc.update(
        "insert into order_lines(order_id,product_id,quantity) values (?, ?, ?)",
        SECOND,
        product,
        5);
    token = OrderBearer.issue(jdbc, issuer, null, "SUPPORT");
  }

  @Test
  @DisplayName("Listado ordenado por unidades descendentes")
  void should_order_by_units_when_sort_is_requested() throws Exception {
    mvc.perform(
            get("/api/orders")
                .param("status", "PAID")
                .param("sort", "totalUnits DESC")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(SECOND.toString()))
        .andExpect(jsonPath("$[0].totalUnits").value(5))
        .andExpect(jsonPath("$[1].id").value(FIRST.toString()))
        .andExpect(jsonPath("$[1].totalUnits").value(2));
  }

  @Test
  @DisplayName("Listado sin sort usa el orden predeterminado")
  void should_order_by_id_when_sort_is_absent() throws Exception {
    mvc.perform(
            get("/api/orders").param("status", "PAID").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(FIRST.toString()))
        .andExpect(jsonPath("$[1].id").value(SECOND.toString()));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("Ordenación inyectada se rechaza y conserva pedidos")
  void should_reject_injected_sort_when_listing_orders() throws Exception {
    UUID product = UUID.randomUUID();
    jdbc.update("insert into stock(product_id, available) values (?, ?)", product, 17);
    var orders = jdbc.queryForList("select * from orders order by id");
    var lines = jdbc.queryForList("select * from order_lines order by order_id, product_id");
    var stock = jdbc.queryForList("select * from stock order by product_id");
    try {
      assertAll(
          () ->
              assertDoesNotThrow(
                  this::assertInjectedSortRejected,
                  "La ordenación inyectada debe rechazarse con HTTP 400"),
          () -> assertEquals(orders, jdbc.queryForList("select * from orders order by id")),
          () ->
              assertEquals(
                  lines,
                  jdbc.queryForList("select * from order_lines order by order_id, product_id")),
          () -> assertEquals(stock, jdbc.queryForList("select * from stock order by product_id")),
          () ->
              mvc.perform(
                      get("/api/orders")
                          .param("status", "PAID")
                          .header("Authorization", "Bearer " + token))
                  .andExpect(status().isOk()));
    } finally {
      // Sin transacción de test: el seed de @BeforeEach se confirma y hay que retirarlo a mano.
      var customers =
          jdbc.queryForList(
              "select distinct customer_id from orders where id in (?, ?)",
              UUID.class,
              FIRST,
              SECOND);
      jdbc.update("delete from order_lines where order_id in (?, ?)", FIRST, SECOND);
      jdbc.update("delete from orders where id in (?, ?)", FIRST, SECOND);
      customers.forEach(customer -> jdbc.update("delete from customers where id = ?", customer));
      jdbc.update("delete from stock where product_id = ?", product);
    }
  }

  private void assertInjectedSortRejected() throws Exception {
    mvc.perform(
            get("/api/orders")
                .param("status", "PAID")
                .param("sort", "totalUnits; drop table orders--")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/invalid-order-sort"));
  }
}
