package com.codeja.master_acdc.ordering.infrastructure.config;

import com.codeja.master_acdc.ordering.domain.port.PaymentGateway;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.payment.RestPaymentGateway;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PaymentConfiguration {
  /**
   * Cliente HTTP de la pasarela como bean propio: es {@link AutoCloseable} y Spring lo cierra al
   * parar el contexto (destroyMethod inferido {@code close}).
   */
  @Bean
  public HttpClient paymentHttpClient(@Value("${shop.payments.timeout:PT2S}") Duration timeout) {
    return HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(timeout)
        .build();
  }

  @Bean
  public PaymentGateway paymentGateway(
      HttpClient paymentHttpClient,
      @Value("${shop.payments.base-url:}") String baseUrl,
      @Value("${shop.payments.timeout:PT2S}") Duration timeout) {
    var requests = new JdkClientHttpRequestFactory(paymentHttpClient);
    requests.setReadTimeout(timeout);
    return new RestPaymentGateway(
        RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build());
  }
}
