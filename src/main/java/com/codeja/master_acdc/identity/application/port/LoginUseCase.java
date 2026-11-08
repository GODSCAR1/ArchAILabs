package com.codeja.master_acdc.identity.application.port;

import com.codeja.master_acdc.identity.domain.model.Login;
import com.codeja.master_acdc.identity.domain.model.LoginResult;

public interface LoginUseCase {
  LoginResult login(Login login);
}
