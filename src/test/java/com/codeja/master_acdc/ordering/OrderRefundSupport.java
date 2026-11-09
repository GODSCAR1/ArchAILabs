package com.codeja.master_acdc.ordering;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.codeja.master_acdc.OrderBearer;
import com.codeja.master_acdc.identity.IdentityIntegrationSupport;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

abstract class OrderRefundSupport extends IdentityIntegrationSupport {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired AccessTokenIssuer issuer;
  UUID order;
  UUID customer;
  UUID product;

  void seed(String status) {
    order = UUID.randomUUID();
    customer = UUID.randomUUID();
    product = UUID.randomUUID();
    jdbc.update("insert into stock(product_id,available) values (?,7)", product);
    jdbc.update(
        "insert into orders(id,customer_id,status) values (?,?,?)", order, customer, status);
    jdbc.update(
        "insert into order_lines(order_id,product_id,quantity) values (?,?,3)", order, product);
  }

  String bearer(String role) {
    return "Bearer "
        + OrderBearer.issue(jdbc, issuer, role.equals("CUSTOMER") ? customer : null, role);
  }

  ResultActions refund(String id, String bearer) throws Exception {
    return mvc.perform(post("/api/orders/{id}/refund", id).header("Authorization", bearer));
  }

  String statusOfOrder() {
    return jdbc.queryForObject("select status from orders where id=?", String.class, order);
  }

  int stock() {
    return jdbc.queryForObject(
        "select available from stock where product_id=?", Integer.class, product);
  }
}
