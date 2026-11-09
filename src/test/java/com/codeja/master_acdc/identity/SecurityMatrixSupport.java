package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.OrderBearer;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogProduct;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

abstract class SecurityMatrixSupport extends IdentityIntegrationSupport {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired AccessTokenIssuer issuer;
  @Autowired MongoTemplate mongo;
  @Autowired CacheManager caches;
  @Autowired ObjectMapper json;
  UUID customer;
  UUID foreignCustomer;
  UUID own;
  UUID foreign;
  UUID paid;
  UUID foreignPaid;
  UUID product;
  String newSku;
  String manager;

  @BeforeEach
  void seed_independent_resources() throws Exception {
    customer = UUID.randomUUID();
    foreignCustomer = UUID.randomUUID();
    manager = bearer("CATALOG_MANAGER");
    OrderBearer.issue(jdbc, issuer, foreignCustomer, "CUSTOMER");
    newSku = "MATRIX-" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
    removeProduct("A01");
    var created =
        mvc.perform(createProduct("A01").header("Authorization", manager))
            .andExpect(status().isCreated())
            .andReturn();
    product =
        UUID.fromString(
            json.readTree(created.getResponse().getContentAsString()).get("id").asString());
    jdbc.update("insert into stock(product_id,available) values (?,10)", product);
    own = seedOrder(customer, "PENDING");
    foreign = seedOrder(foreignCustomer, "PENDING");
    paid = seedOrder(customer, "PAID");
    foreignPaid = seedOrder(foreignCustomer, "PAID");
  }

  private UUID seedOrder(UUID owner, String state) {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into orders(id,customer_id,status) values (?,?,?)", id, owner, state);
    jdbc.update(
        "insert into order_lines(order_id,product_id,quantity) values (?,?,2)", id, product);
    return id;
  }

  String bearer(String... roles) {
    return "Bearer " + OrderBearer.issue(jdbc, issuer, customer, roles);
  }

  MockHttpServletRequestBuilder createProduct(String sku) {
    return post("/api/products")
        .contentType("application/json")
        .content(json.writeValueAsString(Map.of("sku", sku, "name", "Matrix", "price", 100)));
  }

  String orderBody() {
    return json.writeValueAsString(
        Map.of("lines", List.of(Map.of("productId", product, "quantity", 2))));
  }

  Snapshot snapshot() {
    return new Snapshot(
        jdbc.queryForList("select * from orders order by id"),
        jdbc.queryForList("select * from order_lines order by order_id,product_id"),
        jdbc.queryForList("select * from stock order by product_id"),
        mongo.findAll(CatalogProduct.class));
  }

  @AfterEach
  void clean_independent_resources() {
    jdbc.update(
        "delete from order_lines where order_id in (select id from orders where customer_id in"
            + " (?,?))",
        customer,
        foreignCustomer);
    jdbc.update("delete from orders where customer_id in (?,?)", customer, foreignCustomer);
    jdbc.update("delete from user_account where customer_id in (?,?)", customer, foreignCustomer);
    jdbc.update("delete from customers where id in (?,?)", customer, foreignCustomer);
    jdbc.update("delete from stock where product_id=?", product);
    removeProduct("A01");
    removeProduct(newSku);
  }

  private void removeProduct(String sku) {
    mongo.remove(Query.query(Criteria.where("sku").is(sku)), CatalogProduct.class);
    caches.getCache("products").evict(sku);
  }

  void assertProblem(org.springframework.test.web.servlet.ResultActions result, int code)
      throws Exception {
    String type =
        switch (code) {
          case 401 -> "unauthorized";
          case 403 -> "forbidden";
          case 404 -> "order-not-found";
          default -> throw new IllegalArgumentException("Unexpected rejection: " + code);
        };
    result
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentTypeCompatibleWith("application/problem+json"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status")
                .value(code))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.type")
                .value("https://api.master-acdc.dev/problems/" + type));
  }

  record Snapshot(
      List<Map<String, Object>> orders,
      List<Map<String, Object>> lines,
      List<Map<String, Object>> stock,
      List<CatalogProduct> products) {
    void assertSame(Snapshot other) {
      assertThat(other.orders).containsExactlyElementsOf(orders);
      assertThat(other.lines).containsExactlyElementsOf(lines);
      assertThat(other.stock).containsExactlyElementsOf(stock);
      assertThat(other.products).containsExactlyInAnyOrderElementsOf(products);
    }
  }
}
