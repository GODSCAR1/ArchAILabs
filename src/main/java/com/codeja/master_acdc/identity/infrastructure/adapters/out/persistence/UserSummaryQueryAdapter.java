package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.identity.domain.model.UserPage;
import com.codeja.master_acdc.identity.domain.model.UserPageRequest;
import com.codeja.master_acdc.identity.domain.port.UserSummaryQuery;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public class UserSummaryQueryAdapter implements UserSummaryQuery {
  private final UserAccountJpaRepository users;

  public UserSummaryQueryAdapter(UserAccountJpaRepository users) {
    this.users = users;
  }

  @Override
  public UserPage list(UserPageRequest request) {
    long total = users.count();
    if ((long) request.page() * request.size() >= total) {
      return new UserPage(List.of(), request, total);
    }
    var page = PageRequest.of(request.page(), request.size(), Sort.by("id").ascending());
    var content =
        users.findSummaries(page).stream().map(UserSummaryPersistenceMapper::toSummary).toList();
    return new UserPage(content, request, total);
  }
}
