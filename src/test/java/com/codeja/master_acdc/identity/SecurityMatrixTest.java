package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogProduct;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TestPropertySource(
    properties = {
      "catalog.cache.enabled=true",
      "spring.jpa.properties.hibernate.generate_statistics=true"
    })
// Supresión aprobada (lab S16b): la matriz de seguridad concentra a propósito todos los endpoints
// y roles en una clase (ADR 0011); dividirla rompería la lectura de la matriz.
@SuppressWarnings("PMD.GodClass")
class SecurityMatrixTest extends SecurityMatrixSupport {
  @org.springframework.beans.factory.annotation.Autowired
  private jakarta.persistence.EntityManagerFactory factory;

  @org.springframework.beans.factory.annotation.Autowired
  private com.codeja.master_acdc.identity.domain.port.Passwords passwords;

  enum Endpoint {
    PRODUCT("GET /api/products/A01", 200, 200, 200, 200, 200),
    CREATE_PRODUCT("POST /api/products", 401, 403, 201, 403, 201),
    EDIT_PRODUCT("PUT /api/products/{id}", 401, 403, 200, 403, 200),
    RESERVE("POST /api/products/{id}/reservations", 401, 403, 201, 403, 201),
    CREATE_ORDER("POST /api/orders", 401, 201, 403, 403, 403),
    LIST("GET /api/orders?status=PAID", 401, 200, 403, 200, 200),
    OWN("GET /api/orders/{propio}", 401, 200, 403, 200, 200),
    FOREIGN("GET /api/orders/{ajeno}", 401, 404, 403, 200, 200),
    CANCEL_OWN("POST /api/orders/{propio}/cancellation", 401, 200, 403, 403, 403),
    CANCEL_FOREIGN("POST /api/orders/{ajeno}/cancellation", 401, 404, 403, 403, 403),
    REFUND("POST /api/orders/{id}/refund", 401, 403, 403, 200, 200),
    USERS("GET /api/admin/users", 401, 403, 403, 403, 200),
    BLOCK("POST /api/admin/users/{id}/block", 401, 403, 403, 403, 204),
    UNBLOCK("POST /api/admin/users/{id}/unblock", 401, 403, 403, 403, 204),
    RESET_REQUEST("POST /api/auth/password-reset/request", 202, 202, 202, 202, 202),
    RESET_CONFIRM("POST /api/auth/password-reset/confirm", 204, 204, 204, 204, 204),
    EXPORT("GET /api/admin/exports?name=pedidos.csv", 401, 403, 403, 403, 200),
    HEALTH("GET /actuator/health", 200, 200, 200, 200, 200),
    METRICS("GET /actuator/metrics", 401, 401, 401, 401, 401);

    private final String label;
    private final int[] codes;

    Endpoint(String label, int... codes) {
      this.label = label;
      this.codes = codes;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private static final java.util.Set<Endpoint> S13 =
      java.util.EnumSet.of(Endpoint.RESET_REQUEST, Endpoint.RESET_CONFIRM, Endpoint.EXPORT);

  static Stream<Arguments> matrix() {
    return cells(Stream.of(Endpoint.values()).filter(endpoint -> !S13.contains(endpoint)));
  }

  static Stream<Arguments> recoveryAndExportMatrix() {
    return cells(S13.stream());
  }

  private static Stream<Arguments> cells(Stream<Endpoint> endpoints) {
    var roles = List.of("Anónimo", "CUSTOMER", "CATALOG_MANAGER", "SUPPORT", "ADMIN");
    return endpoints.flatMap(
        endpoint ->
            java.util.stream.IntStream.range(0, roles.size())
                .mapToObj(
                    index -> Arguments.of(endpoint, roles.get(index), endpoint.codes[index])));
  }

  @ParameterizedTest(name = "{0} × {1}")
  @MethodSource("matrix")
  @DisplayName("Matriz de acceso con recursos válidos")
  void should_enforce_matrix_when_endpoint_is_called_with_role(
      Endpoint endpoint, String role, int code) throws Exception {
    exercise(endpoint, role, code);
  }

  @ParameterizedTest(name = "{0} × {1}")
  @MethodSource("recoveryAndExportMatrix")
  @DisplayName("Matriz de recuperación y descarga administrativa")
  void should_enforce_matrix_when_recovery_or_export_is_called_with_role(
      Endpoint endpoint, String role, int code) throws Exception {
    exercise(endpoint, role, code);
  }

  private void exercise(Endpoint endpoint, String role, int code) throws Exception {
    var request =
        (S13.contains(endpoint) ? recoveryOrExportRequest(endpoint) : request(endpoint))
            .secure(true);
    if (!"Anónimo".equals(role)) {
      request.header("Authorization", bearer(role));
    }
    var before = snapshot();
    var result = mvc.perform(request).andExpect(status().is(code));
    if (code >= 400) {
      assertProblem(result, code);
      before.assertSame(snapshot());
    } else {
      assertSuccess(endpoint, role, before, result.andReturn());
    }
  }

  private MockHttpServletRequestBuilder request(Endpoint endpoint) throws Exception {
    return switch (endpoint) {
      case CREATE_PRODUCT -> createProduct(newSku);
      case EDIT_PRODUCT ->
          put("/api/products/{id}", product)
              .contentType("application/json")
              .content(
                  json.writeValueAsString(
                      Map.of(
                          "name",
                          "Edited",
                          "price",
                          200,
                          "version",
                          mongo.findById(product, CatalogProduct.class).version())));
      case RESERVE ->
          post("/api/products/{id}/reservations", product)
              .contentType("application/json")
              .content("{\"units\":2}");
      case CREATE_ORDER -> post("/api/orders").contentType("application/json").content(orderBody());
      case CANCEL_OWN -> post("/api/orders/{id}/cancellation", own);
      case CANCEL_FOREIGN -> post("/api/orders/{id}/cancellation", foreign);
      case REFUND -> post("/api/orders/{id}/refund", paid);
      case BLOCK -> post("/api/admin/users/{id}/block", blockTarget);
      case UNBLOCK -> post("/api/admin/users/{id}/unblock", unblockTarget);
      default -> readRequest(endpoint);
    };
  }

  private MockHttpServletRequestBuilder recoveryOrExportRequest(Endpoint endpoint)
      throws Exception {
    return switch (endpoint) {
      case RESET_REQUEST -> resetRequest();
      case RESET_CONFIRM -> resetConfirm(issueResetToken());
      case EXPORT -> get("/api/admin/exports").param("name", "pedidos.csv");
      default -> throw new IllegalArgumentException("Not a lab S13 endpoint: " + endpoint);
    };
  }

  private MockHttpServletRequestBuilder readRequest(Endpoint endpoint) {
    return switch (endpoint) {
      case PRODUCT -> get("/api/products/A01");
      case LIST -> get("/api/orders").param("status", "PAID");
      case OWN -> get("/api/orders/{id}", own);
      case FOREIGN -> get("/api/orders/{id}", foreign);
      case USERS -> get("/api/admin/users");
      case HEALTH -> get("/actuator/health");
      case METRICS -> get("/actuator/metrics");
      default -> throw new IllegalArgumentException("Not a read: " + endpoint);
    };
  }

  private void assertSuccess(Endpoint endpoint, String role, Snapshot before, MvcResult result)
      throws Exception {
    var expected = before.copy();
    if (endpoint == Endpoint.BLOCK || endpoint == Endpoint.UNBLOCK) {
      assertThat(result.getResponse().getContentAsString()).isEmpty();
      managementEffect(endpoint, expected);
      expected.assertSame(snapshot());
      return;
    }
    if (endpoint == Endpoint.RESET_REQUEST
        || endpoint == Endpoint.RESET_CONFIRM
        || endpoint == Endpoint.EXPORT) {
      nonJsonEffect(endpoint, expected, result);
      expected.assertSame(snapshot());
      return;
    }
    var body = json.readTree(result.getResponse().getContentAsString());
    applyEffect(endpoint, role, expected, body);
    var after = snapshot();
    assertThat(after.orders()).containsExactlyInAnyOrderElementsOf(expected.orders());
    assertThat(after.lines()).containsExactlyInAnyOrderElementsOf(expected.lines());
    assertThat(after.stock()).containsExactlyInAnyOrderElementsOf(expected.stock());
    assertThat(after.products()).containsExactlyInAnyOrderElementsOf(expected.products());
    assertThat(after.identity().accounts())
        .containsExactlyInAnyOrderElementsOf(expected.identity().accounts());
    assertThat(after.identity().families())
        .containsExactlyInAnyOrderElementsOf(expected.identity().families());
  }

  private void managementEffect(Endpoint endpoint, Snapshot expected) {
    UUID target = endpoint == Endpoint.BLOCK ? blockTarget : unblockTarget;
    change(expected.identity().accounts(), "id", target, "enabled", endpoint == Endpoint.UNBLOCK);
    if (endpoint == Endpoint.BLOCK) {
      change(expected.identity().accounts(), "id", target, "session_version", 6L);
      var after = snapshot().identity().families();
      var targetFamilies =
          expected.identity().families().stream()
              .filter(row -> target.equals(row.get("user_id")))
              .toList();
      assertThat(targetFamilies).hasSize(2);
      for (var family : targetFamilies) {
        var actual =
            after.stream()
                .filter(row -> row.get("id").equals(family.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(actual.get("revoked_at")).isNotNull();
        change(
            expected.identity().families(),
            "id",
            (UUID) family.get("id"),
            "revoked_at",
            actual.get("revoked_at"));
      }
    }
  }

  private void nonJsonEffect(Endpoint endpoint, Snapshot expected, MvcResult result)
      throws Exception {
    long tokens =
        jdbc.queryForObject(
            "select count(*) from password_reset_token where user_id=?",
            Long.class,
            recoveryTarget);
    switch (endpoint) {
      case RESET_REQUEST -> {
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(notifier.lastMessageFor(recoveryEmail)).isPresent();
        assertThat(tokens).isOne();
      }
      case RESET_CONFIRM -> {
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        String hash =
            jdbc.queryForObject(
                "select password_hash from user_account where id=?", String.class, recoveryTarget);
        assertThat(passwords.matches(RECOVERED_PASSWORD, hash)).isTrue();
        change(expected.identity().accounts(), "id", recoveryTarget, "password_hash", hash);
        change(expected.identity().accounts(), "id", recoveryTarget, "session_version", 6L);
        assertThat(tokens).isZero();
      }
      case EXPORT -> {
        assertThat(result.getResponse().getContentType())
            .isEqualTo(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE);
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(EXPORT_BYTES);
        assertThat(Files.readAllBytes(exportDirectory.resolve("pedidos.csv")))
            .isEqualTo(EXPORT_BYTES);
      }
      default -> throw new IllegalArgumentException("Not a non-JSON endpoint: " + endpoint);
    }
  }

  private void applyEffect(
      Endpoint endpoint, String role, Snapshot expected, tools.jackson.databind.JsonNode body) {
    switch (endpoint) {
      case CREATE_ORDER -> assertCreatedOrder(expected, body);
      case CANCEL_OWN -> {
        change(expected.orders(), "id", own, "status", "CANCELLED");
        change(expected.stock(), "product_id", product, "available", 12);
        assertOrderBody(body, own, "CANCELLED");
      }
      case REFUND -> {
        change(expected.orders(), "id", paid, "status", "REFUNDED");
        assertOrderBody(body, paid, "REFUNDED");
      }
      case RESERVE -> {
        change(expected.stock(), "product_id", product, "available", 8);
        assertThat(body.get("productId").asString()).isEqualTo(product.toString());
        assertThat(body.get("units").asInt()).isEqualTo(2);
        assertThat(body.get("available").asInt()).isEqualTo(8);
      }
      case CREATE_PRODUCT, EDIT_PRODUCT -> assertProductEffect(endpoint, expected.products(), body);
      default -> assertRead(endpoint, role, body);
    }
  }

  private void assertCreatedOrder(Snapshot expected, tools.jackson.databind.JsonNode body) {
    UUID id = UUID.fromString(body.get("id").asString());
    expected.orders().add(Map.of("id", id, "customer_id", customer, "status", "PENDING"));
    expected.lines().add(Map.of("order_id", id, "product_id", product, "quantity", 2));
    change(expected.stock(), "product_id", product, "available", 8);
    assertOrderBody(body, id, "PENDING");
  }

  private void assertOrderBody(tools.jackson.databind.JsonNode body, UUID id, String state) {
    assertThat(body.get("id").asString()).isEqualTo(id.toString());
    assertThat(body.get("status").asString()).isEqualTo(state);
    assertThat(body.has("customerId")).isFalse();
    assertThat(body.get("lines").size()).isEqualTo(1);
    assertThat(body.get("lines").get(0).get("productId").asString()).isEqualTo(product.toString());
    assertThat(body.get("lines").get(0).get("quantity").asInt()).isEqualTo(2);
  }

  private void assertProductEffect(
      Endpoint endpoint, List<CatalogProduct> expected, tools.jackson.databind.JsonNode body) {
    UUID id = UUID.fromString(body.get("id").asString());
    var actual = mongo.findById(id, CatalogProduct.class);
    if (endpoint == Endpoint.EDIT_PRODUCT) {
      var old = expected.stream().filter(p -> p.id().equals(product)).findFirst().orElseThrow();
      assertThat(id).isEqualTo(product);
      expected.remove(old);
      expected.add(
          new CatalogProduct(
              old.id(),
              old.sku(),
              "Edited",
              200,
              old.status(),
              old.createdAt(),
              old.version() + 1,
              old.category(),
              old.variants(),
              old.attributes(),
              old.availableStock(),
              old.rating(),
              old.latestReviews()));
      assertThat(body.get("version").asLong()).isEqualTo(old.version() + 1);
    } else {
      assertThat(actual.sku()).isEqualTo(newSku);
      assertThat(actual.name()).isEqualTo("Matrix");
      assertThat(actual.price()).isEqualTo(100);
      assertThat(actual.version()).isZero();
      assertThat(actual.createdAt()).isNotNull();
      expected.add(actual);
    }
  }

  private void assertRead(Endpoint endpoint, String role, tools.jackson.databind.JsonNode body) {
    switch (endpoint) {
      case OWN -> assertOrderBody(body, own, "PENDING");
      case FOREIGN -> assertOrderBody(body, foreign, "PENDING");
      case LIST -> {
        var ids = new ArrayList<String>();
        body.forEach(
            row -> {
              ids.add(row.get("id").asString());
              assertThat(row.get("lineCount").asInt()).isEqualTo(1);
              assertThat(row.get("totalUnits").asInt()).isEqualTo(2);
              assertThat(row.has("customerId")).isFalse();
            });
        assertThat(ids)
            .containsExactlyInAnyOrderElementsOf(
                "CUSTOMER".equals(role)
                    ? List.of(paid.toString())
                    : List.of(paid.toString(), foreignPaid.toString()));
      }
      case PRODUCT -> {
        assertThat(body.get("id").asString()).isEqualTo(product.toString());
        assertThat(body.get("sku").asString()).isEqualTo("A01");
      }
      case HEALTH -> assertThat(body.get("status").asString()).isEqualTo("UP");
      case USERS ->
          body.get("content")
              .forEach(
                  row ->
                      assertThat(row.propertyNames())
                          .containsExactlyInAnyOrder("id", "email", "roles", "enabled"));
      default -> throw new IllegalArgumentException("Not a read: " + endpoint);
    }
  }

  private void change(
      List<Map<String, Object>> rows, String key, UUID id, String field, Object value) {
    var row = rows.stream().filter(r -> id.equals(r.get(key))).findFirst().orElseThrow();
    var replacement = new HashMap<>(row);
    replacement.put(field, value);
    rows.set(rows.indexOf(row), replacement);
  }

  @Test
  @DisplayName("Rol desconocido no concede acceso")
  void should_deny_without_changes_when_role_is_unknown() throws Exception {
    var request = request(Endpoint.LIST).header("Authorization", bearer("UNKNOWN"));
    var before = snapshot();
    assertProblem(mvc.perform(request).andExpect(status().isForbidden()), 403);
    before.assertSame(snapshot());
  }

  @Test
  @DisplayName("Rol desconocido no elimina un rol conocido")
  void should_read_foreign_order_when_support_is_mixed_with_unknown_role() throws Exception {
    var request = request(Endpoint.FOREIGN).header("Authorization", bearer("SUPPORT", "UNKNOWN"));
    var before = snapshot();
    var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
    assertSuccess(Endpoint.FOREIGN, "SUPPORT", before, result);
  }

  @Test
  @DisplayName("Roles combinados suman permisos sin perder restricciones")
  void should_create_and_reserve_when_customer_is_also_catalog_manager() throws Exception {
    String token = bearer("CUSTOMER", "CATALOG_MANAGER");
    var before = snapshot();
    var created =
        mvc.perform(request(Endpoint.CREATE_ORDER).header("Authorization", token))
            .andExpect(status().isCreated())
            .andReturn();
    assertSuccess(Endpoint.CREATE_ORDER, "CUSTOMER", before, created);
    var afterOrder = snapshot();
    var reserved =
        mvc.perform(request(Endpoint.RESERVE).header("Authorization", token))
            .andExpect(status().isCreated())
            .andReturn();
    var expected = new ArrayList<>(afterOrder.stock());
    change(expected, "product_id", product, "available", 6);
    new Snapshot(
            afterOrder.orders(),
            afterOrder.lines(),
            expected,
            afterOrder.products(),
            afterOrder.identity())
        .assertSame(snapshot());
    assertThat(json.readTree(reserved.getResponse().getContentAsString()).get("available").asInt())
        .isEqualTo(6);
  }

  @Test
  @DisplayName("ADMIN no puede crear pedidos por su rol")
  void should_deny_creation_before_queries_when_admin_has_no_customer_permission()
      throws Exception {
    assertDeniedWithoutQueries(request(Endpoint.CREATE_ORDER), "ADMIN");
    assertDeniedWithoutQueries(request(Endpoint.CANCEL_OWN), "ADMIN");
  }

  @ParameterizedTest(name = "GET /api/orders/{0} × CATALOG_MANAGER")
  @ValueSource(strings = {"own", "foreign", "missing"})
  @DisplayName("Ausencia de permiso de lectura oculta existencia")
  void should_hide_existence_before_queries_when_catalog_manager_reads_orders(String target)
      throws Exception {
    UUID id =
        switch (target) {
          case "own" -> own;
          case "foreign" -> foreign;
          default -> UUID.randomUUID();
        };
    assertDeniedWithoutQueries(get("/api/orders/{id}", id), "CATALOG_MANAGER");
  }

  @ParameterizedTest(name = "POST /api/orders/{0}/cancellation × SUPPORT")
  @ValueSource(strings = {"own", "foreign", "missing"})
  @DisplayName("Lectura global no concede cancelación")
  void should_deny_cancellation_before_queries_when_support_has_global_read(String target)
      throws Exception {
    UUID id =
        switch (target) {
          case "own" -> own;
          case "foreign" -> foreign;
          default -> UUID.randomUUID();
        };
    assertDeniedWithoutQueries(post("/api/orders/{id}/cancellation", id), "SUPPORT");
  }

  private void assertDeniedWithoutQueries(MockHttpServletRequestBuilder request, String role)
      throws Exception {
    request.header("Authorization", bearer(role));
    var before = snapshot();
    var statistics = factory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
    statistics.clear();
    var result = mvc.perform(request).andExpect(status().isForbidden());
    assertProblem(result, 403);
    assertThat(statistics.getPrepareStatementCount()).isZero();
    before.assertSame(snapshot());
  }
}
