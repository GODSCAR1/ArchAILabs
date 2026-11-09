package com.codeja.master_acdc.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.catalog.application.port.CreateProductUseCase;
import com.codeja.master_acdc.catalog.application.port.EditProductUseCase;
import com.codeja.master_acdc.catalog.domain.port.ProductEditingRepository;
import com.codeja.master_acdc.catalog.domain.port.ProductRepository;
import com.codeja.master_acdc.inventory.application.port.ReserveProductStockUseCase;
import com.codeja.master_acdc.inventory.domain.port.StockReservationRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class MethodAuthorizationTest extends IdentityIntegrationSupport {
  @Autowired private com.codeja.master_acdc.identity.application.port.BlockUserUseCase blockUser;

  @Autowired
  private com.codeja.master_acdc.identity.application.port.UnblockUserUseCase unblockUser;

  @MockitoSpyBean private com.codeja.master_acdc.identity.domain.port.UserAccounts accounts;

  @ParameterizedTest
  @ValueSource(strings = {"ROLE_ADMIN", "user:manage"})
  @DisplayName("Gestión de cuenta exige doble autorización")
  void should_deny_without_delegation_when_either_admin_requirement_is_missing(String authority)
      throws Exception {
    clearInvocations(accounts);
    com.codeja.master_acdc.AuthorizedCalls.withAuthorities(
        () -> {
          assertThatThrownBy(() -> blockUser.block(null)).isInstanceOf(AccessDeniedException.class);
          assertThatThrownBy(() -> unblockUser.unblock(null))
              .isInstanceOf(AccessDeniedException.class);
          return null;
        },
        authority);
    for (String action : java.util.List.of("block", "unblock")) {
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                      "/api/admin/users/not-uuid/" + action)
                  .with(
                      org.springframework.security.test.web.servlet.request
                          .SecurityMockMvcRequestPostProcessors.user("actor")
                          .authorities(
                              new org.springframework.security.core.authority
                                  .SimpleGrantedAuthority(authority))))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(accounts);
  }

  @Autowired private com.codeja.master_acdc.identity.application.port.ListUsersUseCase listUsers;
  @Autowired private org.springframework.test.web.servlet.MockMvc mvc;
  @MockitoSpyBean private com.codeja.master_acdc.identity.domain.port.UserSummaryQuery users;
  @Autowired private com.codeja.master_acdc.ordering.application.port.RefundOrderUseCase refund;
  @Autowired private CreateProductUseCase create;
  @Autowired private EditProductUseCase edit;
  @Autowired private ReserveProductStockUseCase reserve;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private ProductRepository products;
  @MockitoSpyBean private ProductEditingRepository editing;
  @MockitoSpyBean private StockReservationRepository stock;

  @Autowired
  private com.codeja.master_acdc.ordering.application.port.CreateOrderUseCase createOrder;

  @Autowired
  private com.codeja.master_acdc.ordering.application.port.CancelOrderUseCase cancelOrder;

  @Autowired private com.codeja.master_acdc.ordering.application.port.GetOrderUseCase getOrder;
  @MockitoSpyBean private com.codeja.master_acdc.ordering.api.CurrentOrderActor orderActor;
  @MockitoSpyBean private com.codeja.master_acdc.ordering.domain.port.OrderRepository orders;

  @Autowired
  private com.codeja.master_acdc.ordering.application.port.ListOrdersByStatusUseCase listOrders;

  @MockitoSpyBean private com.codeja.master_acdc.ordering.domain.port.OrderSummaryQuery summaries;

  @BeforeEach
  void clear_repository_observations() {
    clearInvocations(products, editing, stock);
    clearInvocations(orderActor, orders);
    clearInvocations(summaries);
    clearInvocations(users);
  }

  @ParameterizedTest
  @ValueSource(strings = {"create", "edit", "reserve"})
  @WithMockUser(authorities = "order:create")
  @DisplayName("Permisos antes de consultar propiedad")
  void should_deny_before_delegation_when_bean_caller_lacks_permission(String command) {
    assertThatThrownBy(() -> invoke(command)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(products, editing, stock);
  }

  @ParameterizedTest
  @ValueSource(strings = {"create", "edit", "reserve"})
  void should_reject_before_delegation_when_bean_caller_has_no_authentication(String command) {
    assertThatThrownBy(() -> invoke(command))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    verifyNoInteractions(products, editing, stock);
  }

  @Test
  @WithMockUser(authorities = "product:write")
  void should_delegate_create_and_edit_when_caller_has_product_write() {
    assertThat(AopUtils.isAopProxy(create)).isTrue();
    assertThat(AopUtils.isAopProxy(edit)).isTrue();
    var product =
        create.create(
            "Authorized",
            "AUTH-" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT),
            BigDecimal.TEN);
    var changed = edit.edit(product.id(), "Changed", BigDecimal.ONE, 0L);
    assertThat(changed.name().value()).isEqualTo("Changed");
    verify(products).save(product);
    verify(editing).findById(product.id());
  }

  @Test
  @WithMockUser(authorities = "stock:reserve")
  void should_delegate_reservation_when_caller_has_stock_reserve() {
    assertThat(AopUtils.isAopProxy(reserve)).isTrue();
    UUID id = UUID.randomUUID();
    jdbc.update("insert into stock(product_id, available) values (?, 2)", id);
    assertThat(reserve.reserve(id, 1).available()).isEqualTo(1);
    verify(stock).findForUpdateById(id);
    assertThat(
            jdbc.queryForObject(
                "select available from stock where product_id=?", Integer.class, id))
        .isEqualTo(1);
  }

  private void invoke(String command) {
    switch (command) {
      case "create" -> create.create("Product", "AUTH-TEST", BigDecimal.TEN);
      case "edit" -> edit.edit(UUID.randomUUID(), "Product", BigDecimal.TEN, 0L);
      case "reserve" -> reserve.reserve(UUID.randomUUID(), 1);
      default -> throw new IllegalArgumentException(command);
    }
  }

  @Test
  @WithMockUser(authorities = "product:write")
  void should_deny_order_commands_before_identity_lookup_when_permissions_are_absent() {
    assertThatThrownBy(
            () ->
                createOrder.create(
                    null,
                    java.util.List.of(
                        new com.codeja.master_acdc.ordering.domain.model.RequestedOrderLine(
                            null, 0))))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> cancelOrder.cancel(null, null))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                getOrder.get(
                    null,
                    com.codeja.master_acdc.ordering.domain.model.OrderReadScope.Global.INSTANCE))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(orderActor, orders);
  }

  @Test
  @WithMockUser(authorities = "product:write")
  void should_deny_listing_before_identity_lookup_when_permission_is_absent() {
    assertThatThrownBy(() -> listOrders.list(globalPaid()))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(orderActor, summaries);
  }

  @Test
  @WithMockUser(authorities = "order:read-own")
  void should_ignore_supplied_global_scope_when_listing_bean_is_called_by_customer() {
    UUID customer = UUID.randomUUID();
    var own =
        new com.codeja.master_acdc.ordering.domain.model.OrderListingCriteria(
            com.codeja.master_acdc.ordering.domain.model.OrderStatus.PAID,
            new com.codeja.master_acdc.ordering.domain.model.OrderReadScope.Own(customer));
    org.mockito.Mockito.doReturn(
            java.util.Optional.of(
                new com.codeja.master_acdc.ordering.api.CurrentOrderActor.Actor(
                    UUID.randomUUID(), customer)))
        .when(orderActor)
        .current();
    org.mockito.Mockito.doReturn(java.util.List.of()).when(summaries).findByStatus(own);
    assertThat(AopUtils.isAopProxy(listOrders)).isTrue();
    assertThat(listOrders.list(globalPaid())).isEmpty();
    verify(orderActor).current();
    verify(summaries).findByStatus(own);
  }

  private com.codeja.master_acdc.ordering.domain.model.OrderListingCriteria globalPaid() {
    return new com.codeja.master_acdc.ordering.domain.model.OrderListingCriteria(
        com.codeja.master_acdc.ordering.domain.model.OrderStatus.PAID,
        com.codeja.master_acdc.ordering.domain.model.OrderReadScope.Global.INSTANCE);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void should_deny_before_query_when_admin_lacks_user_read() throws Exception {
    assertThat(AopUtils.isAopProxy(listUsers)).isTrue();
    assertThatThrownBy(
            () ->
                listUsers.list(
                    new com.codeja.master_acdc.identity.domain.model.UserPageRequest(0, 20)))
        .isInstanceOf(AccessDeniedException.class);
    mvc.perform(get("/api/admin/users"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("https://api.master-acdc.dev/problems/forbidden"));
    mvc.perform(get("/api/admin/users").param("size", "invalid")).andExpect(status().isForbidden());
    verifyNoInteractions(users);
  }

  @Test
  @WithMockUser(authorities = "user:read")
  void should_deny_at_http_chain_when_user_read_lacks_admin_role() throws Exception {
    mvc.perform(get("/api/admin/users"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("https://api.master-acdc.dev/problems/forbidden"));
    verifyNoInteractions(users);
  }

  @Test
  @WithMockUser(authorities = "order:read-any")
  void should_deny_refund_before_identity_lookup_when_refund_permission_is_absent() {
    assertThat(AopUtils.isAopProxy(refund)).isTrue();
    assertThatThrownBy(() -> refund.refund(null)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(orderActor, orders);
  }
}
