package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogProduct;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@org.springframework.test.context.TestPropertySource(properties = "catalog.cache.enabled=true")
class ApiSecurityTest extends AuthHttpSupport {
  @Autowired private MongoTemplate mongo;
  @Autowired private org.springframework.cache.CacheManager caches;
  private UUID product;

  @BeforeEach
  void seed_stock() {
    product = UUID.randomUUID();
    jdbc.update("insert into stock(product_id, available) values (?, 2)", product);
  }

  @Test
  @DisplayName("Lectura pública del catálogo")
  void should_read_without_session_when_catalog_get_is_anonymous() throws Exception {
    var browser = session();
    String sku = ("SEC-" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT);
    mvc.perform(create(browser, sku)).andExpect(status().isCreated());
    var response =
        mvc.perform(get("/api/products/{sku}", sku).secure(true))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(response.getRequest().getSession(false)).isNull();
    assertThat(response.getResponse().getCookie("JSESSIONID")).isNull();
    mvc.perform(get("/api/categories").secure(true)).andExpect(status().isNotFound());
    mvc.perform(get("/api/categories/any").secure(true)).andExpect(status().isNotFound());
    problem(authorizedPost("/api/categories"), 401, "unauthorized");
    problem(get("/api/auth/login").secure(true), 401, "unauthorized");
    problem(authorizedPost("/api/auth/login/extra"), 401, "unauthorized");
  }

  @Test
  @DisplayName("Reserva bajo products no es pública")
  void should_reject_before_inventory_when_nested_reservation_is_anonymous() throws Exception {
    var before = business();
    problem(reservation(), 401, "unauthorized");
    assertThat(business()).isEqualTo(before);
  }

  @Test
  void should_deny_other_methods_when_jwks_only_allows_public_get() throws Exception {
    problem(post("/.well-known/jwks.json").secure(true), 403, "forbidden");
  }

  @Test
  @DisplayName("Sesión HTTP no sustituye bearer")
  void should_ignore_session_identity_when_bearer_is_absent() throws Exception {
    var before = business();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated("old-session", "unused", List.of()));
    var session = new MockHttpSession();
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    problem(
        get("/api/orders").secure(true).param("status", "PAID").session(session),
        401,
        "unauthorized");
    assertThat(business()).isEqualTo(before);
  }

  @Test
  @DisplayName("Error bearer conserva challenge y formato")
  void should_preserve_protocol_error_when_bearer_is_invalid() throws Exception {
    var before = business();
    problem(
            get("/api/orders")
                .secure(true)
                .param("status", "PAID")
                .header("Authorization", "Bearer invalid"),
            401,
            "unauthorized")
        .andExpect(
            header()
                .string(
                    "WWW-Authenticate",
                    org.hamcrest.Matchers.startsWith("Bearer error=\"invalid_token\"")));
    assertThat(business()).isEqualTo(before);
  }

  @Test
  @DisplayName("Error CSRF conserva formato de problema")
  void should_use_denied_handler_when_public_logout_has_no_csrf() throws Exception {
    var browser = session();
    var before = business();
    var families = tokens(browser.user());
    problem(post("/api/auth/logout").secure(true).cookie(browser.refresh()), 403, "forbidden");
    assertThat(tokens(browser.user())).isEqualTo(families);
    assertThat(business()).isEqualTo(before);
  }

  @Test
  @DisplayName("Pedido anónimo se rechaza antes de validar cabecera")
  void should_authenticate_before_header_validation_when_order_is_anonymous() throws Exception {
    var before = business();
    problem(authorizedPost("/api/orders").content("{\"lines\":[]}"), 401, "unauthorized");
    assertThat(business()).isEqualTo(before);
  }

  @Test
  @DisplayName("Pedido autenticado conserva validación de cabecera")
  void should_keep_customer_header_contract_when_order_has_bearer() throws Exception {
    var browser = session();
    var before = business();
    problem(
        authorizedPost("/api/orders")
            .header("Authorization", "Bearer " + browser.access())
            .content("{\"lines\":[]}"),
        400,
        "missing-customer-id");
    assertThat(business()).isEqualTo(before);
    UUID customer =
        jdbc.queryForObject(
            "select customer_id from user_account where id=?", UUID.class, browser.user());
    mvc.perform(
            authorizedPost("/api/orders")
                .header("Authorization", "Bearer " + browser.access())
                .header("X-Customer-Id", customer)
                .content("{\"lines\":[{\"productId\":\"" + product + "\",\"quantity\":1}]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.customerId").value(customer.toString()));
  }

  @Test
  @DisplayName("Listado y cancelación requieren JWT")
  void should_not_change_orders_when_listing_or_cancelling_is_anonymous() throws Exception {
    var before = business();
    problem(get("/api/orders").secure(true).param("status", "PAID"), 401, "unauthorized");
    problem(
        authorizedPost("/api/orders/" + UUID.randomUUID() + "/cancellation"), 401, "unauthorized");
    assertThat(business()).isEqualTo(before);
  }

  @Test
  @DisplayName("Reserva anónima no llega al inventario")
  void should_preserve_stock_when_reservation_lacks_jwt() throws Exception {
    var before = business();
    problem(reservation(), 401, "unauthorized");
    assertThat(business()).isEqualTo(before);
  }

  @Test
  @DisplayName("Reserva con JWT conserva contrato de éxito")
  void should_decrement_stock_when_reservation_has_real_jwt() throws Exception {
    var browser = session();
    var orders = jdbc.queryForList("select * from orders order by id");
    mvc.perform(reservation().header("Authorization", "Bearer " + browser.access()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.productId").value(product.toString()))
        .andExpect(jsonPath("$.units").value(1))
        .andExpect(jsonPath("$.available").value(1));
    assertThat(
            jdbc.queryForObject(
                "select available from stock where product_id=?", Integer.class, product))
        .isEqualTo(1);
    assertThat(jdbc.queryForList("select * from orders order by id")).isEqualTo(orders);
  }

  @Test
  @DisplayName("Escritura anónima no modifica catálogo")
  void should_preserve_catalog_when_create_or_edit_has_no_jwt() throws Exception {
    var browser = session();
    String sku = ("SEC-" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT);
    var created = mvc.perform(create(browser, sku)).andExpect(status().isCreated()).andReturn();
    UUID id =
        UUID.fromString(
            json.readTree(created.getResponse().getContentAsString()).get("id").asString());
    mvc.perform(get("/api/products/{sku}", sku).secure(true)).andExpect(status().isOk());
    var cached = caches.getCache("products").get(sku).get();
    var before = business();
    var products = mongo.findAll(CatalogProduct.class);
    problem(
        authorizedPost("/api/products")
            .content("{\"name\":\"Rejected\",\"sku\":\"REJECTED\",\"price\":100}"),
        401,
        "unauthorized");
    problem(
        put("/api/products/{id}", id)
            .secure(true)
            .contentType("application/json")
            .content("{\"name\":\"Rejected\",\"price\":100,\"version\":0}"),
        401,
        "unauthorized");
    assertThat(mongo.findAll(CatalogProduct.class)).containsExactlyElementsOf(products);
    assertThat(caches.getCache("products").get(sku).get()).isEqualTo(cached);
    assertThat(business()).isEqualTo(before);
  }

  @Test
  @DisplayName("Edición autenticada conserva conflicto de versión")
  void should_preserve_product_when_real_bearer_edits_stale_version() throws Exception {
    var browser = session();
    String sku = ("SEC-" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT);
    var result = mvc.perform(create(browser, sku)).andExpect(status().isCreated()).andReturn();
    UUID id =
        UUID.fromString(
            json.readTree(result.getResponse().getContentAsString()).get("id").asString());
    mvc.perform(edit(browser, id, 0)).andExpect(status().isOk());
    var before = business();
    var productBefore = mongo.findById(id, CatalogProduct.class);
    var detail =
        mvc.perform(get("/api/products/{sku}", sku).secure(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var cached = caches.getCache("products").get(sku).get();
    problem(edit(browser, id, 0), 409, "concurrent-modification");
    assertThat(caches.getCache("products").get(sku).get()).isEqualTo(cached);
    assertThat(mongo.findById(id, CatalogProduct.class)).isEqualTo(productBefore);
    assertThat(
            mvc.perform(get("/api/products/{sku}", sku).secure(true))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .isEqualTo(detail);
    assertThat(business()).isEqualTo(before);
  }

  private MockHttpServletRequestBuilder create(Browser browser, String sku) {
    return authorizedPost("/api/products")
        .header("Authorization", "Bearer " + browser.access())
        .content(
            json.writeValueAsString(Map.of("name", "Security product", "sku", sku, "price", 100)));
  }

  private MockHttpServletRequestBuilder edit(Browser browser, UUID id, int version) {
    return put("/api/products/{id}", id)
        .secure(true)
        .contentType("application/json")
        .header("Authorization", "Bearer " + browser.access())
        .content(
            json.writeValueAsString(Map.of("name", "Edited", "price", 200, "version", version)));
  }

  private MockHttpServletRequestBuilder reservation() {
    return authorizedPost("/api/products/" + product + "/reservations").content("{\"units\":1}");
  }

  private List<List<Map<String, Object>>> business() {
    return List.of(
        jdbc.queryForList("select * from orders order by id"),
        jdbc.queryForList("select * from stock order by product_id"));
  }
}
