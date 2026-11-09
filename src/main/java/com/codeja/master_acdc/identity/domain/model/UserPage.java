package com.codeja.master_acdc.identity.domain.model;

import java.util.List;

public record UserPage(List<UserSummary> content, UserPageRequest request, long totalElements) {
  public UserPage {
    content = List.copyOf(content);
  }

  public long totalPages() {
    return totalElements / request.size() + (totalElements % request.size() == 0 ? 0 : 1);
  }
}
