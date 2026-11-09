package com.codeja.master_acdc.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class OrderRefundIT extends OrderRefundSupport {
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  private com.codeja.master_acdc.ordering.domain.port.OrderCancelledPublisher cancellations;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  private com.codeja.master_acdc.inventory.api.InventoryFacade inventory;

  @org.junit.jupiter.api.BeforeEach
  void clear_effect_observations() {
    org.mockito.Mockito.clearInvocations(cancellations, inventory);
  }

  @org.junit.jupiter.api.AfterEach
  void verify_refund_has_no_inventory_or_cancellation_effects() {
    org.mockito.Mockito.verifyNoInteractions(cancellations, inventory);
  }

  @ParameterizedTest
  @CsvSource({"PAID,SUPPORT", "SHIPPED,SUPPORT", "PAID,ADMIN", "SHIPPED,ADMIN"})
  @DisplayName("Reembolso desde PAID o SHIPPED")
  void should_refund_without_stock_changes_when_order_is_paid_or_shipped(String state, String role)
      throws Exception {
    seed(state);
    refund(order.toString(), bearer(role))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(order.toString()))
        .andExpect(jsonPath("$.status").value("REFUNDED"))
        .andExpect(jsonPath("$.customerId").doesNotExist())
        .andExpect(jsonPath("$.lines[0].productId").value(product.toString()))
        .andExpect(jsonPath("$.lines[0].quantity").value(3));
    assertThat(statusOfOrder()).isEqualTo("REFUNDED");
    assertThat(stock()).isEqualTo(7);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "CANCELLED", "REFUNDED"})
  @DisplayName("Reembolso rechazado por estado")
  void should_reject_without_changes_when_order_is_not_refundable(String state) throws Exception {
    seed(state);
    refund(order.toString(), bearer("SUPPORT"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/order-not-refundable"));
    assertThat(statusOfOrder()).isEqualTo(state);
    assertThat(stock()).isEqualTo(7);
  }

  @ParameterizedTest
  @ValueSource(strings = {"00000000-0000-0000-0000-000000000000", "no-uuid"})
  @DisplayName("Reembolso de pedido inexistente")
  void should_return_not_found_when_order_id_is_missing_or_invalid(String id) throws Exception {
    seed("PAID");
    refund(id, bearer("SUPPORT"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.type").value("https://api.master-acdc.dev/problems/order-not-found"));
    assertThat(statusOfOrder()).isEqualTo("PAID");
    assertThat(stock()).isEqualTo(7);
  }
}
