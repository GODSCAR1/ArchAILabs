package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.ordering.application.port.CancelOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.CreateOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.ListOrdersByStatusUseCase;
import com.codeja.master_acdc.ordering.domain.exception.DuplicateProductException;
import com.codeja.master_acdc.ordering.domain.exception.EmptyOrderException;
import com.codeja.master_acdc.ordering.domain.exception.InsufficientStockException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotCancellableException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.exception.UnknownProductException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import com.codeja.master_acdc.ordering.domain.model.OrderSummary;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

  private static final String TYPE_BASE = "https://api.master-acdc.dev/problems/";
  private static final String CUSTOMER = "550e8400-e29b-41d4-a716-446655440000";
  private static final String A = "11111111-1111-1111-1111-111111111111";
  private static final String B = "22222222-2222-2222-2222-222222222222";

  @Autowired MockMvc mockMvc;

  @MockitoBean CancelOrderUseCase cancelOrderUseCase;
  @MockitoBean CreateOrderUseCase createOrderUseCase;
  @MockitoBean ListOrdersByStatusUseCase listOrdersByStatusUseCase;

  @Test
  void should_respond_201_when_request_valid() throws Exception {
    given(createOrderUseCase.create(any(), anyList()))
        .willReturn(
            new Order(
                UUID.randomUUID(),
                UUID.fromString(CUSTOMER),
                OrderStatus.PENDING,
                List.of(new OrderLine(UUID.fromString(A), 3))));

    perform(CUSTOMER, lines(line(A, "3")))
        .andExpect(status().isCreated())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.customerId").value(CUSTOMER))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.lines[0].productId").value(A))
        .andExpect(jsonPath("$.lines[0].quantity").value(3));
  }

  @Test
  void should_respond_400_missing_customer_id_when_header_absent() throws Exception {
    perform(null, lines(line(A, "3")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "missing-customer-id"));
  }

  @Test
  void should_respond_400_invalid_customer_id_when_not_uuid() throws Exception {
    perform("no-es-uuid", lines(line(A, "3")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "invalid-customer-id"));
  }

  @Test
  void should_respond_422_empty_order_when_no_lines() throws Exception {
    given(createOrderUseCase.create(any(), anyList())).willThrow(new EmptyOrderException());

    perform(CUSTOMER, lines())
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "empty-order"));
  }

  @Test
  void should_respond_422_duplicate_product_when_product_repeated() throws Exception {
    given(createOrderUseCase.create(any(), anyList()))
        .willThrow(new DuplicateProductException(UUID.fromString(A)));

    perform(CUSTOMER, lines(line(A, "2"), line(A, "3")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "duplicate-product"));
  }

  @Test
  void should_respond_422_invalid_quantity_when_quantity_not_positive() throws Exception {
    perform(CUSTOMER, lines(line(A, "0"), line(B, "2")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "invalid-quantity"));
  }

  @Test
  void should_respond_422_unknown_product_when_inventory_lacks_record() throws Exception {
    given(createOrderUseCase.create(any(), anyList())).willThrow(new UnknownProductException());

    perform(CUSTOMER, lines(line(A, "3")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "unknown-product"));
  }

  @Test
  void should_respond_409_insufficient_stock_when_stock_lacking() throws Exception {
    given(createOrderUseCase.create(any(), anyList())).willThrow(new InsufficientStockException());

    perform(CUSTOMER, lines(line(A, "3")))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "insufficient-stock"));
  }

  @Test
  void should_respond_400_when_header_error_and_body_and_stock_errors() throws Exception {
    given(createOrderUseCase.create(any(), anyList())).willThrow(new InsufficientStockException());

    perform(null, lines(line(A, "0"), line(B, "2")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "missing-customer-id"));
  }

  private ResultActions perform(String customerId, String body) throws Exception {
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

  @Test
  void should_respond_400_missing_customer_id_when_header_absent_on_cancel() throws Exception {
    mockMvc
        .perform(post("/api/orders/{id}/cancellation", "550e8400-e29b-41d4-a716-446655440000"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "missing-customer-id"));
  }

  @Test
  void should_respond_400_invalid_customer_id_when_header_not_uuid_on_cancel() throws Exception {
    mockMvc
        .perform(
            post("/api/orders/{id}/cancellation", "550e8400-e29b-41d4-a716-446655440000")
                .header("X-Customer-Id", "no-es-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "invalid-customer-id"));
  }

  @Test
  void should_respond_404_order_not_found_when_order_id_not_uuid() throws Exception {
    mockMvc
        .perform(post("/api/orders/{id}/cancellation", "no-uuid").header("X-Customer-Id", CUSTOMER))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-found"));
  }

  @Test
  void should_respond_404_order_not_found_when_use_case_throws() throws Exception {
    given(cancelOrderUseCase.cancel(any(), any())).willThrow(new OrderNotFoundException(null));

    mockMvc
        .perform(
            post("/api/orders/{id}/cancellation", "550e8400-e29b-41d4-a716-446655440000")
                .header("X-Customer-Id", CUSTOMER))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-found"));
  }

  @Test
  void should_respond_409_order_not_cancellable_when_use_case_throws() throws Exception {
    given(cancelOrderUseCase.cancel(any(), any()))
        .willThrow(new OrderNotCancellableException(null));

    mockMvc
        .perform(
            post("/api/orders/{id}/cancellation", "550e8400-e29b-41d4-a716-446655440000")
                .header("X-Customer-Id", CUSTOMER))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "order-not-cancellable"));
  }

  @Test
  void should_respond_200_cancelled_when_use_case_returns() throws Exception {
    given(cancelOrderUseCase.cancel(any(), any()))
        .willReturn(
            new Order(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                UUID.fromString(CUSTOMER),
                OrderStatus.CANCELLED,
                List.of(new OrderLine(UUID.fromString(A), 3))));

    mockMvc
        .perform(
            post("/api/orders/{id}/cancellation", "550e8400-e29b-41d4-a716-446655440000")
                .header("X-Customer-Id", CUSTOMER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  @DisplayName("Pedidos PAID listados con nombre y métricas")
  void should_return_paid_summaries_when_status_is_paid() throws Exception {
    given(listOrdersByStatusUseCase.list(OrderStatus.PAID))
        .willReturn(List.of(new OrderSummary(UUID.randomUUID(), "Ada", 2, 5, List.of())));

    mockMvc
        .perform(get("/api/orders").param("status", "PAID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].customerName").value("Ada"))
        .andExpect(jsonPath("$[0].lineCount").value(2))
        .andExpect(jsonPath("$[0].totalUnits").value(5))
        .andExpect(jsonPath("$[0].productIds").isArray());
  }

  @Test
  @DisplayName("Status en minúsculas")
  void should_reject_lowercase_status_when_status_name_is_not_exact() throws Exception {
    mockMvc
        .perform(get("/api/orders").param("status", "paid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "invalid-order-status"));
  }

  @Test
  @DisplayName("Sin parámetro status")
  void should_reject_missing_status_when_status_parameter_is_absent() throws Exception {
    mockMvc
        .perform(get("/api/orders"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(TYPE_BASE + "missing-order-status"));
  }
}
