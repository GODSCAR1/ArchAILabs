package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.identity.domain.model.IdentityException;
import com.codeja.master_acdc.identity.domain.model.UserPage;
import com.codeja.master_acdc.identity.domain.model.UserPageRequest;
import com.codeja.master_acdc.identity.domain.model.UserSummary;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.util.LinkedMultiValueMap;

class AdminUserWebMapperTest {
  @Test
  void should_accept_only_canonical_uuid_when_mapping_management_target() {
    UUID id = UUID.randomUUID();
    assertThat(AdminUserWebMapper.targetId(id.toString())).isEqualTo(id);
    for (String invalid : List.of("not-uuid", "1-1-1-1-1", "")) {
      assertThatThrownBy(() -> AdminUserWebMapper.targetId(invalid)).hasMessage("user-not-found");
    }
  }

  @Test
  void should_use_defaults_when_no_pagination_is_supplied() {
    assertThat(AdminUserWebMapper.request(new LinkedMultiValueMap<>()))
        .isEqualTo(new UserPageRequest(0, 20));
  }

  @ParameterizedTest
  @CsvSource({
    "page,-1",
    "size,0",
    "size,101",
    "page,abc",
    "size,abc",
    "page,2147483648",
    "sort,password_hash"
  })
  void should_reject_request_when_pagination_or_sort_is_invalid(String key, String value) {
    var params = new LinkedMultiValueMap<String, String>();
    params.add(key, value);
    assertThatThrownBy(() -> AdminUserWebMapper.request(params))
        .isInstanceOf(IdentityException.class)
        .hasMessage("invalid-user-page");
  }

  @Test
  void should_reject_request_when_pagination_is_repeated() {
    var params = new LinkedMultiValueMap<String, String>();
    params.addAll("page", List.of("0", "1"));
    assertThatThrownBy(() -> AdminUserWebMapper.request(params))
        .isInstanceOf(IdentityException.class);
  }

  @Test
  void should_map_public_fields_and_totals_when_page_has_accounts() {
    var user = new UserSummary(UUID.randomUUID(), "fixture@example.com", Set.of("CUSTOMER"), false);
    var result =
        AdminUserWebMapper.response(new UserPage(List.of(user), new UserPageRequest(2, 20), 41));
    assertThat(result)
        .isEqualTo(
            new AdminUsersResponse(
                List.of(new AdminUsersResponse.User(user.id(), user.email(), user.roles(), false)),
                2,
                20,
                41,
                3));
  }
}
