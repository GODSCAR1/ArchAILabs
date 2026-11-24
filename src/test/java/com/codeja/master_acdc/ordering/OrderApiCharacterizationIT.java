package com.codeja.master_acdc.ordering;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.OrderBearer;
import com.codeja.master_acdc.identity.domain.port.AccessTokenIssuer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class OrderApiCharacterizationIT extends IntegrationTest {
  private final UUID customer = UUID.randomUUID();
  private final UUID order = UUID.randomUUID();
  private final UUID product = UUID.randomUUID();
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AccessTokenIssuer issuer;
  private MockMvcTester tester;
  private String customerToken;

  @BeforeEach
  void prepare_order() {
    tester = MockMvcTester.create(mvc);
    customerToken = token(customer, "CUSTOMER");
    jdbc.update(
        """
        insert into orders (id, customer_id, status, payment_amount)
        values (?, ?, 'PENDING', 100)
        """,
        order,
        customer);
    jdbc.update(
        """
        insert into order_lines (order_id, product_id, quantity) values (?, ?, 3)
        """,
        order,
        product);
    jdbc.update("insert into stock (product_id, available) values (?, 7)", product);
  }

  @Test
  @DisplayName("Creación devuelve el pedido completo")
  void should_return_created_order_when_stock_is_available() throws Exception {
    MvcTestResult result = perform(post("/api/orders"), customerToken, lines(2));
    UUID created =
        UUID.fromString(
            jdbc.queryForObject("select id from orders where id <> ?", UUID.class, order)
                .toString());
    assertJson(result, 201, orderJson(created, "PENDING", 2));
  }

  @Test
  @DisplayName("Listado devuelve solo los pedidos propios")
  void should_return_owned_summaries_when_listing_orders() {
    assertJson(
        perform(get("/api/orders").param("status", "PENDING"), customerToken, null),
        200,
        """
        [{"id":"%s","customerName":"Order fixture","lineCount":1,"totalUnits":3}]
        """
            .formatted(order));
    assertJson(
        perform(
            get("/api/orders").param("status", "PENDING"),
            token(UUID.randomUUID(), "CUSTOMER"),
            null),
        200,
        "[]");
  }

  @Test
  @DisplayName("Detalle devuelve todas las líneas")
  void should_return_order_when_owner_requests_detail() {
    assertJson(
        perform(get("/api/orders/{id}", order), customerToken, null),
        200,
        orderJson(order, "PENDING", 3));
  }

  @Test
  @DisplayName("Cancelación devuelve el pedido cancelado")
  void should_return_cancelled_order_when_owner_cancels() {
    assertJson(
        perform(post("/api/orders/{id}/cancellation", order), customerToken, null),
        200,
        orderJson(order, "CANCELLED", 3));
  }

  @Test
  @DisplayName("Confirmación escribe la fila completa del outbox")
  void should_return_paid_order_and_write_outbox_when_gateway_authorizes() {
    UUID second = UUID.randomUUID();
    jdbc.update(
        """
        insert into order_lines (order_id, product_id, quantity) values (?, ?, 2)
        """,
        order,
        second);
    jdbc.update("insert into stock (product_id, available) values (?, 5)", second);
    wiremock()
        .register(
            com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/authorizations"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"AUTHORIZED\"}")));
    assertJsonIgnoringArrayOrder(
        perform(post("/api/orders/{id}/confirmation", order), customerToken, null),
        200,
        """
        {"id":"%s","status":"PAID","lines":[
          {"productId":"%s","quantity":3},{"productId":"%s","quantity":2}]}
        """
            .formatted(order, product, second));
    assertSingleOutboxRow(product, second);
  }

  private void assertSingleOutboxRow(UUID... productIds) {
    var rows =
        jdbc.query(
            """
            select order_id, product_ids, created_at from order_confirmation_outbox
            where order_id = ?
            """,
            (row, index) ->
                new OutboxRow(
                    row.getObject("order_id", UUID.class),
                    List.of((UUID[]) row.getArray("product_ids").getArray()),
                    row.getTimestamp("created_at") != null),
            order);
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().orderId()).isEqualTo(order);
    assertThat(rows.getFirst().productIds()).containsExactlyInAnyOrder(productIds);
    assertThat(rows.getFirst().hasCreatedAt()).isTrue();
  }

  @Test
  @DisplayName("Reembolso devuelve el pedido reembolsado")
  void should_return_refunded_order_when_support_refunds_paid_order() {
    jdbc.update("update orders set status = 'PAID' where id = ?", order);
    assertJson(
        perform(post("/api/orders/{id}/refund", order), token(null, "SUPPORT"), null),
        200,
        orderJson(order, "REFUNDED", 3));
  }

  @ParameterizedTest
  @CsvSource({
    "POST, /api/orders, CUSTOMER",
    "GET, /api/orders, CUSTOMER",
    "GET, /api/orders/00000000-0000-0000-0000-000000000001, CUSTOMER",
    "POST, /api/orders/00000000-0000-0000-0000-000000000001/cancellation, CUSTOMER",
    "POST, /api/orders/00000000-0000-0000-0000-000000000001/confirmation, CUSTOMER",
    "POST, /api/orders/00000000-0000-0000-0000-000000000001/refund, SUPPORT"
  })
  @DisplayName("Cada endpoint rechaza solicitudes sin token o sin permiso")
  void should_return_security_problem_when_token_is_missing_or_role_lacks_permission(
      String method, String path, String allowedRole) {
    String body = "/api/orders".equals(path) && "POST".equals(method) ? lines(1) : null;
    assertProblem(request(method, path, null, body), 401, "unauthorized", "unauthorized", path);
    String role = "SUPPORT".equals(allowedRole) ? "CUSTOMER" : "CATALOG_MANAGER";
    assertProblem(
        request(method, path, token(UUID.randomUUID(), role), body),
        403,
        "forbidden",
        "forbidden",
        path);
  }

  @ParameterizedTest
  @CsvSource({"GET, ''", "POST, /cancellation", "POST, /confirmation"})
  @DisplayName("Un pedido ajeno no revela su existencia")
  void should_hide_foreign_order_when_customer_accesses_it(String method, String suffix) {
    String path = "/api/orders/" + order + suffix;
    assertProblem(
        request(method, path, token(UUID.randomUUID(), "CUSTOMER"), null),
        404,
        "order-not-found",
        "order not found: " + order,
        path);
  }

  @ParameterizedTest
  @CsvSource({
    "'{\"lines\":[]}', empty-order, order must have at least one line",
    "'{\"lines\":[{\"quantity\":1}]}', invalid-line, product id must not be null"
  })
  @DisplayName("La creación devuelve errores de validación completos")
  void should_return_validation_problem_when_order_lines_are_invalid(
      String body, String slug, String detail) {
    assertProblem(
        perform(post("/api/orders"), customerToken, body), 422, slug, detail, "/api/orders");
  }

  @Test
  @DisplayName("La creación informa de cantidad, duplicados, producto y stock")
  void should_return_specific_problem_when_creation_fails() {
    assertProblem(
        perform(post("/api/orders"), customerToken, lines(0)),
        422,
        "invalid-quantity",
        "quantity must be greater than 0",
        "/api/orders");
    String line = "{\"productId\":\"" + product + "\",\"quantity\":1}";
    assertProblem(
        perform(post("/api/orders"), customerToken, "{\"lines\":[" + line + "," + line + "]}"),
        422,
        "duplicate-product",
        "product repeated: " + product,
        "/api/orders");
    assertProblem(
        perform(post("/api/orders"), customerToken, lines(8)),
        409,
        "insufficient-stock",
        "insufficient stock",
        "/api/orders");
    String unknown = "{\"lines\":[{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}]}";
    assertProblem(
        perform(post("/api/orders"), customerToken, unknown),
        422,
        "unknown-product",
        "unknown product",
        "/api/orders");
  }

  @Test
  @DisplayName("Listado informa de status y ordenación inválidos")
  void should_return_listing_problem_when_criteria_are_invalid() {
    assertProblem(
        perform(get("/api/orders"), customerToken, null),
        400,
        "missing-order-status",
        "order status is required",
        "/api/orders");
    assertProblem(
        perform(get("/api/orders").param("status", "INVALID"), customerToken, null),
        400,
        "invalid-order-status",
        "invalid order status: INVALID",
        "/api/orders");
    assertProblem(
        perform(
            get("/api/orders").param("status", "PENDING").param("sort", "bad"),
            customerToken,
            null),
        400,
        "invalid-order-sort",
        "Ordenación de pedidos no válida",
        "/api/orders");
  }

  @Test
  @DisplayName("Cambios de estado inválidos devuelven conflictos completos")
  void should_return_conflict_problem_when_order_state_rejects_action() {
    jdbc.update("update orders set status = 'SHIPPED' where id = ?", order);
    String cancellation = "/api/orders/" + order + "/cancellation";
    assertProblem(
        request("POST", cancellation, customerToken, null),
        409,
        "order-not-cancellable",
        "order not cancellable: " + order + " in status SHIPPED",
        cancellation);
    String confirmation = "/api/orders/" + order + "/confirmation";
    assertProblem(
        request("POST", confirmation, customerToken, null),
        409,
        "order-not-confirmable",
        "order not confirmable: " + order,
        confirmation);
    jdbc.update("update orders set status = 'PENDING' where id = ?", order);
    String refund = "/api/orders/" + order + "/refund";
    assertProblem(
        request("POST", refund, token(null, "SUPPORT"), null),
        409,
        "order-not-refundable",
        "order not refundable: " + order,
        refund);
  }

  @ParameterizedTest
  @CsvSource({
    "200, DECLINED, 402, payment-declined, payment declined",
    "500, ERROR, 502, payment-unavailable, payment gateway unavailable",
    "200, AUTHORIZED, 504, payment-timeout, payment gateway timed out"
  })
  @DisplayName("Errores de pasarela mantienen el cuerpo completo")
  void should_return_payment_problem_when_gateway_fails(
      int gatewayStatus, String gatewayBody, int status, String slug, String detail) {
    wiremock()
        .register(
            com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/authorizations"))
                .willReturn(
                    aResponse()
                        .withFixedDelay(status == 504 ? 4000 : 0)
                        .withStatus(gatewayStatus)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"" + gatewayBody + "\"}")));
    String path = "/api/orders/" + order + "/confirmation";
    assertProblem(request("POST", path, customerToken, null), status, slug, detail, path);
  }

  @Test
  @DisplayName("Precio ausente impide confirmar el pedido")
  void should_return_price_problem_when_catalog_product_is_missing() {
    jdbc.update("update orders set payment_amount = null where id = ?", order);
    String path = "/api/orders/" + order + "/confirmation";
    assertProblem(
        request("POST", path, customerToken, null),
        422,
        "payment-price-unavailable",
        "payment price unavailable",
        path);
  }

  @Test
  @DisplayName("Reembolso de pedido inexistente")
  void should_return_not_found_when_support_refunds_missing_order() {
    UUID missing = UUID.randomUUID();
    String path = "/api/orders/" + missing + "/refund";
    assertProblem(
        request("POST", path, token(null, "SUPPORT"), null),
        404,
        "order-not-found",
        "order not found: " + missing,
        path);
  }

  private MvcTestResult request(String method, String path, String bearer, String body) {
    return perform("GET".equals(method) ? get(path) : post(path), bearer, body);
  }

  private MvcTestResult perform(MockHttpServletRequestBuilder request, String bearer, String body) {
    if (bearer != null) {
      request.header("Authorization", "Bearer " + bearer);
    }
    if (body != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return tester.perform(request);
  }

  private String token(UUID owner, String role) {
    return OrderBearer.issue(jdbc, issuer, owner, role);
  }

  private String lines(int quantity) {
    return "{\"lines\":[{\"productId\":\"" + product + "\",\"quantity\":" + quantity + "}]}";
  }

  private String orderJson(UUID id, String state, int quantity) {
    return """
    {"id":"%s","status":"%s","lines":[{"productId":"%s","quantity":%d}]}
    """
        .formatted(id, state, product, quantity);
  }

  private static void assertJson(MvcTestResult result, int status, String expected) {
    assertThat(result).hasStatus(status);
    assertThat(result).bodyJson().isStrictlyEqualTo(expected);
  }

  /**
   * Lab S17: order_lines no guarda la posición y el mapeo no fija un orden; Postgres puede devolver
   * las líneas por el índice (order_id, product_id). El contrato no garantiza el orden de las
   * líneas: se compara sin campos de más ni de menos, pero sin exigir el orden de los arrays.
   */
  private static void assertJsonIgnoringArrayOrder(
      MvcTestResult result, int status, String expected) {
    assertThat(result).hasStatus(status);
    assertThat(result)
        .bodyJson()
        .isEqualTo(expected, JsonAssert.comparator(JSONCompareMode.NON_EXTENSIBLE));
  }

  private static void assertProblem(
      MvcTestResult result, int status, String slug, String detail, String path) {
    String title = org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase();
    assertJson(
        result,
        status,
        """
        {"type":"https://api.master-acdc.dev/problems/%s","title":"%s","status":%d,
         "detail":"%s","instance":"%s"}
        """
            .formatted(slug, title, status, detail, path));
  }

  private record OutboxRow(UUID orderId, List<UUID> productIds, boolean hasCreatedAt) {}
}
