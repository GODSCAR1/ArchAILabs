package com.codeja.master_acdc.ordering.infrastructure.adapters.out.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.codeja.master_acdc.ordering.domain.exception.PaymentTimeoutException;
import com.codeja.master_acdc.ordering.domain.exception.PaymentUnavailableException;
import com.codeja.master_acdc.ordering.domain.model.PaymentAttempt;
import com.codeja.master_acdc.ordering.domain.model.PaymentResult;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestPaymentGatewayTest {
  private final RestClient.Builder builder = RestClient.builder().baseUrl("http://payment.test");
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final RestPaymentGateway gateway = new RestPaymentGateway(builder.build());

  @Test
  @DisplayName("La autorización envía importe, moneda y clave del intento")
  void should_send_idempotency_key_and_amount_when_authorizing() {
    UUID id = UUID.randomUUID();
    server
        .expect(requestTo("http://payment.test/authorizations"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Idempotency-Key", "order-" + id + "-2"))
        .andExpect(content().json("{\"amount\":123.45,\"currency\":\"COP\"}"))
        .andRespond(withSuccess("{\"status\":\"AUTHORIZED\"}", MediaType.APPLICATION_JSON));

    assertThat(gateway.authorize(id, new PaymentAttempt(new BigDecimal("123.45"), 2)))
        .isEqualTo(PaymentResult.AUTHORIZED);
    server.verify();
  }

  @Test
  @DisplayName("Un fallo de la pasarela no se reintenta automáticamente")
  void should_report_unavailable_when_gateway_returns_server_error() {
    server.expect(requestTo("http://payment.test/authorizations")).andRespond(withServerError());

    assertThatThrownBy(
            () -> gateway.authorize(UUID.randomUUID(), new PaymentAttempt(BigDecimal.ONE, 0)))
        .isInstanceOf(PaymentUnavailableException.class);
    server.verify();
  }

  @Test
  @DisplayName("Un timeout se traduce sin reintentar")
  void should_report_timeout_when_gateway_does_not_respond() {
    server
        .expect(requestTo("http://payment.test/authorizations"))
        .andRespond(
            request -> {
              throw new SocketTimeoutException("timeout");
            });

    assertThatThrownBy(
            () -> gateway.authorize(UUID.randomUUID(), new PaymentAttempt(BigDecimal.ONE, 0)))
        .isInstanceOf(PaymentTimeoutException.class);
    server.verify();
  }

  @Test
  @DisplayName("Un rechazo definitivo se conserva como resultado")
  void should_return_declined_when_gateway_declines() {
    server
        .expect(requestTo("http://payment.test/authorizations"))
        .andRespond(withSuccess("{\"status\":\"DECLINED\"}", MediaType.APPLICATION_JSON));

    assertThat(gateway.authorize(UUID.randomUUID(), new PaymentAttempt(BigDecimal.ONE, 0)))
        .isEqualTo(PaymentResult.DECLINED);
    server.verify();
  }
}
