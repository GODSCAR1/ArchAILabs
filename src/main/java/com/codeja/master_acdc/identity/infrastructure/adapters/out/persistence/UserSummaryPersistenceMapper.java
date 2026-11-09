package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.identity.domain.model.UserSummary;
import java.util.Arrays;
import java.util.HashSet;

public final class UserSummaryPersistenceMapper {
  private UserSummaryPersistenceMapper() {}

  public static UserSummary toSummary(UserSummaryRow row) {
    return new UserSummary(
        row.id(), row.email(), new HashSet<>(Arrays.asList(row.roles())), row.enabled());
  }
}
