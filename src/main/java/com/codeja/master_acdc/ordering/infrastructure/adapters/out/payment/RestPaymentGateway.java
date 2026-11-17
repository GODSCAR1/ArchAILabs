package com.codeja.master_acdc.ordering.infrastructure.adapters.out.payment;

import com.codeja.master_acdc.ordering.domain.exception.PaymentTimeoutException;
import com.codeja.master_acdc.ordering.domain.exception.PaymentUnavailableException;
import com.codeja.master_acdc.ordering.domain.model.PaymentAttempt;
import com.codeja.master_acdc.ordering.domain.model.PaymentResult;
import com.codeja.master_acdc.ordering.domain.port.PaymentGateway;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.UUID;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class RestPaymentGateway implements PaymentGateway {
  private final RestClient client;

  public RestPaymentGateway(RestClient client) {
    this.client = client;
  }

  @Override
  public PaymentResult authorize(UUID orderId, PaymentAttempt attempt) {
    try {
      Response response =
          client
              .post()
              .uri("/authorizations")
              .header("Idempotency-Key", "order-" + orderId + "-" + attempt.number())
              .body(new Request(attempt.amount(), "COP"))
              .retrieve()
              .body(Response.class);
      if (response == null || response.status() == null) {
        throw new PaymentUnavailableException(
            new IllegalStateException("payment response has no status"));
      }
      return response.status();
    } catch (ResourceAccessException ex) {
      if (ex.getCause() instanceof SocketTimeoutException
          || ex.getCause() instanceof HttpTimeoutException) {
        throw new PaymentTimeoutException(ex);
      }
      throw new PaymentUnavailableException(ex);
    } catch (RestClientException ex) {
      throw new PaymentUnavailableException(ex);
    }
  }

  record Request(BigDecimal amount, String currency) {}

  record Response(PaymentResult status) {}
}
