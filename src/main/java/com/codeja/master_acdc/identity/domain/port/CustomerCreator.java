package com.codeja.master_acdc.identity.domain.port;

import java.util.UUID;

public interface CustomerCreator {
  UUID create(String name);
}
