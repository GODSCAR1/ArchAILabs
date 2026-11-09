package com.codeja.master_acdc.identity.infrastructure.adapters.out.security;

import com.codeja.master_acdc.identity.domain.model.Role;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class RolesAndPermissionsConverter
    implements Converter<Jwt, Collection<GrantedAuthority>> {
  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Object claim = jwt.getClaims().get("roles");
    if (!(claim instanceof List<?> roles)
        || roles.stream().anyMatch(role -> !(role instanceof String))) {
      return List.of();
    }
    Set<GrantedAuthority> authorities = new LinkedHashSet<>();
    for (Role role : Role.values()) {
      if (roles.contains(role.name())) {
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        role.permissions()
            .forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
      }
    }
    return List.copyOf(authorities);
  }
}
