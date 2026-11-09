package com.codeja.master_acdc.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.UserPage;
import com.codeja.master_acdc.identity.domain.model.UserPageRequest;
import com.codeja.master_acdc.identity.domain.model.UserSummary;
import com.codeja.master_acdc.identity.domain.port.UserSummaryQuery;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ListUsersServiceTest {
  @ParameterizedTest
  @CsvSource({"0,1", "0,20", "1,100", "2147483647,100"})
  void should_return_minimal_page_when_request_is_valid(int page, int size) {
    var query = mock(UserSummaryQuery.class);
    var request = new UserPageRequest(page, size);
    var user = new UserSummary(UUID.randomUUID(), "fixture@example.com", Set.of("ADMIN"), true);
    var expected = new UserPage(List.of(user), request, 121);
    when(query.list(request)).thenReturn(expected);
    assertThat(new ListUsersService(query).list(request)).isSameAs(expected);
    assertThat(expected.totalPages()).isEqualTo((121L + size - 1) / size);
    verify(query).list(request);
  }

  @ParameterizedTest
  @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
  void should_reject_page_when_bounds_are_invalid(int page, int size) {
    assertThatThrownBy(() -> new UserPageRequest(page, size))
        .isInstanceOf(IdentityException.class)
        .hasMessage("invalid-user-page");
  }

  @Test
  void should_report_zero_pages_when_accounts_are_empty() {
    var page = new UserPage(List.of(), new UserPageRequest(0, 20), 0);
    assertThat(page.totalPages()).isZero();
  }
}
