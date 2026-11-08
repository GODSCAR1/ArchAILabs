package com.codeja.master_acdc.ordering.infrastructure.config;

import com.codeja.master_acdc.ordering.api.CustomerRegistrationFacade;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence.CustomerJpaRepository;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence.CustomerRegistrationAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomerRegistrationConfiguration {
  @Bean
  public CustomerRegistrationFacade customerRegistrationFacade(CustomerJpaRepository customers) {
    return new CustomerRegistrationAdapter(customers);
  }
}
