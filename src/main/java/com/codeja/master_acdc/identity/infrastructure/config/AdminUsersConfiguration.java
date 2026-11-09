package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.application.ListUsersService;
import com.codeja.master_acdc.identity.application.port.ListUsersUseCase;
import com.codeja.master_acdc.identity.domain.port.UserSummaryQuery;
import com.codeja.master_acdc.identity.infrastructure.adapters.in.web.AuthorizedListUsersUseCase;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence.UserAccountJpaRepository;
import com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence.UserSummaryQueryAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class AdminUsersConfiguration {
  @Bean
  public UserSummaryQuery userSummaryQuery(UserAccountJpaRepository users) {
    return new UserSummaryQueryAdapter(users);
  }

  @Bean
  public ListUsersUseCase listUsersUseCase(
      UserSummaryQuery users, PlatformTransactionManager manager) {
    var service = new ListUsersService(users);
    var transactions = new TransactionTemplate(manager);
    transactions.setReadOnly(true);
    ListUsersUseCase transactional =
        request -> transactions.execute(status -> service.list(request));
    return new AuthorizedListUsersUseCase(transactional);
  }
}
