package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.application.BlockUserService;
import com.codeja.master_acdc.identity.application.UnblockUserService;
import com.codeja.master_acdc.identity.application.port.BlockUserUseCase;
import com.codeja.master_acdc.identity.application.port.UnblockUserUseCase;
import com.codeja.master_acdc.identity.domain.port.RefreshTokens;
import com.codeja.master_acdc.identity.domain.port.SessionVersionBarrier;
import com.codeja.master_acdc.identity.domain.port.UserAccounts;
import com.codeja.master_acdc.identity.infrastructure.adapters.in.web.AuthorizedBlockUserUseCase;
import com.codeja.master_acdc.identity.infrastructure.adapters.in.web.AuthorizedUnblockUserUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserManagementConfiguration {
  @Bean
  public BlockUserUseCase blockUserUseCase(
      UserAccounts accounts,
      RefreshTokens tokens,
      SessionVersionBarrier barrier,
      Clock clock,
      SessionRevocationTransactions transactions) {
    var service = new BlockUserService(accounts, tokens, barrier, clock);
    return new AuthorizedBlockUserUseCase(id -> transactions.execute(() -> service.block(id)));
  }

  @Bean
  public UnblockUserUseCase unblockUserUseCase(
      UserAccounts accounts, SessionRevocationTransactions transactions) {
    var service = new UnblockUserService(accounts);
    return new AuthorizedUnblockUserUseCase(id -> transactions.execute(() -> service.unblock(id)));
  }
}
