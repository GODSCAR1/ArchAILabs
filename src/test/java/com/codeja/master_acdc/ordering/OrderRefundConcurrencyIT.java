package com.codeja.master_acdc.ordering;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class OrderRefundConcurrencyIT extends OrderRefundSupport {
  @MockitoSpyBean OrderRepository orders;
  private final CyclicBarrier reads = new CyclicBarrier(2);
  private final Set<Long> transactions = ConcurrentHashMap.newKeySet();

  @Test
  @DisplayName("Reembolsos concurrentes no repiten transición")
  void should_commit_once_when_two_refunds_read_paid_in_independent_transactions()
      throws Exception {
    seed("PAID");
    doAnswer(this::synchronizeRead)
        .when(orders)
        .findVisibleById(eq(order), eq(OrderReadScope.Global.INSTANCE));
    List<MvcResult> results = race(false);
    assertRace(results, "order-not-refundable");
    assertThat(statusOfOrder()).isEqualTo("REFUNDED");
    assertThat(stock()).isEqualTo(7);
  }

  @Test
  @DisplayName("Cancelación y reembolso concurrentes")
  void should_apply_only_winner_effects_when_cancellation_and_refund_compete() throws Exception {
    seed("PAID");
    doAnswer(this::synchronizeRead)
        .when(orders)
        .findVisibleById(eq(order), eq(OrderReadScope.Global.INSTANCE));
    doAnswer(this::synchronizeRead).when(orders).findByIdAndCustomerId(order, customer);
    List<MvcResult> results = race(true);
    boolean cancelled = results.get(1).getResponse().getStatus() == 200;
    assertRace(results, cancelled ? "order-not-refundable" : "order-not-cancellable");
    assertThat(statusOfOrder()).isEqualTo(cancelled ? "CANCELLED" : "REFUNDED");
    assertThat(stock()).isEqualTo(cancelled ? 10 : 7);
  }

  private Object synchronizeRead(InvocationOnMock invocation) throws Throwable {
    Object result = invocation.callRealMethod();
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    transactions.add(jdbc.queryForObject("select txid_current()", Long.class));
    reads.await(15, SECONDS);
    return result;
  }

  private List<MvcResult> race(boolean cancel) throws Exception {
    String support = bearer("SUPPORT");
    String owner = bearer("CUSTOMER");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> refund(order.toString(), support).andReturn());
      var second =
          executor.submit(
              () ->
                  cancel
                      ? mvc.perform(
                              post("/api/orders/{id}/cancellation", order)
                                  .header("Authorization", owner))
                          .andReturn()
                      : refund(order.toString(), support).andReturn());
      return List.of(first.get(30, SECONDS), second.get(30, SECONDS));
    }
  }

  private void assertRace(List<MvcResult> results, String conflict) throws Exception {
    assertThat(transactions).hasSize(2);
    assertThat(results)
        .extracting(result -> result.getResponse().getStatus())
        .containsExactlyInAnyOrder(200, 409);
    for (MvcResult result : results) {
      if (result.getResponse().getStatus() == 409) {
        assertThat(result.getResponse().getContentAsString())
            .contains("https://api.master-acdc.dev/problems/" + conflict);
      }
    }
  }
}
