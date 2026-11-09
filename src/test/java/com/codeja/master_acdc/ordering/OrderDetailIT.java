package com.codeja.master_acdc.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class OrderDetailIT extends IdentityIntegrationSupport {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AccessTokenIssuer issuer;
  private UUID owner;
  private UUID order;
  private UUID product;

  @BeforeEach
  void seed_order_and_stock() {
    owner = UUID.randomUUID();
    order = UUID.randomUUID();
    product = UUID.randomUUID();
    jdbc.update("insert into orders(id,customer_id,status) values (?,?,'PENDING')", order, owner);
    jdbc.update(
        "insert into order_lines(order_id,product_id,quantity) values (?,?,3)", order, product);
    jdbc.update("insert into stock(product_id,available) values (?,7)", product);
  }

  @Test
  @DisplayName("Detalle propio y lectura global")
  void should_read_own_or_global_order_when_account_has_read_permission() throws Exception {
    detail(order.toString(), owner, "CUSTOMER")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(order.toString()))
        .andExpect(jsonPath("$.customerId").doesNotExist())
        .andExpect(jsonPath("$.lines[0].quantity").value(3));
    detail(order.toString(), null, "SUPPORT").andExpect(status().isOk());
    detail(order.toString(), null, "ADMIN").andExpect(status().isOk());
    unchanged();
  }

  @Test
  @DisplayName("Detalle ajeno o inexistente")
  void should_hide_missing_malformed_or_foreign_order_when_customer_reads_detail()
      throws Exception {
    for (String id : new String[] {order.toString(), UUID.randomUUID().toString(), "not-uuid"}) {
      detail(id, UUID.randomUUID(), "CUSTOMER")
          .andExpect(status().isNotFound())
          .andExpect(
              jsonPath("$.type").value("https://api.master-acdc.dev/problems/order-not-found"));
    }
    unchanged();
  }

  @Test
  @DisplayName("Ausencia de permiso de lectura oculta existencia")
  void should_reject_before_existence_checks_when_actor_has_no_read_permission() throws Exception {
    for (String id : new String[] {order.toString(), UUID.randomUUID().toString(), "not-uuid"}) {
      detail(id, owner, "CATALOG_MANAGER")
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.type").value("https://api.master-acdc.dev/problems/forbidden"));
    }
    unchanged();
  }

  @Test
  @DisplayName("Cliente ausente no abre el alcance")
  void should_hide_detail_when_customer_account_is_unlinked() throws Exception {
    detail(order.toString(), null, "CUSTOMER").andExpect(status().isNotFound());
    unchanged();
  }

  @Test
  @DisplayName("Usuario multirrol no puede cancelar un pedido ajeno")
  void should_keep_cancellation_owned_when_actor_also_has_global_reading() throws Exception {
    String token = OrderBearer.issue(jdbc, issuer, UUID.randomUUID(), "CUSTOMER", "SUPPORT");
    mvc.perform(get("/api/orders/{id}", order).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/orders/{id}/cancellation", order).header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/order-not-found"));
    unchanged();
  }

  @Test
  void should_reject_missing_account_when_bearer_has_read_permission() throws Exception {
    String token = OrderBearer.issue(jdbc, issuer, null, "SUPPORT");
    UUID id =
        UUID.fromString(com.nimbusds.jwt.SignedJWT.parse(token).getJWTClaimsSet().getSubject());
    jdbc.update("delete from user_account where id=?", id);
    mvc.perform(get("/api/orders/{id}", order).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    unchanged();
  }

  private ResultActions detail(String id, UUID customer, String... roles) throws Exception {
    return mvc.perform(
        get("/api/orders/{id}", id)
            .header("Authorization", "Bearer " + OrderBearer.issue(jdbc, issuer, customer, roles)));
  }

  private void unchanged() {
    assertThat(jdbc.queryForObject("select status from orders where id=?", String.class, order))
        .isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "select available from stock where product_id=?", Integer.class, product))
        .isEqualTo(7);
  }
}
