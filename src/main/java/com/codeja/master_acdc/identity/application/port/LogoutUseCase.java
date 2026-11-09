package com.codeja.master_acdc.identity.application.port;

public interface LogoutUseCase {
  boolean logout(String secret);
}
