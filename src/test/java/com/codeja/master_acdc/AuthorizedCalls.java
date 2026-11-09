package com.codeja.master_acdc;

import java.util.function.Supplier;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Lab S12 (RBAC): ejecuta una llamada directa a un caso de uso protegido con @PreAuthorize en el
 * hilo actual, con las authorities indicadas. Para tests que invocan beans sin pasar por HTTP
 * (fixtures, transacciones, cache). Funciona en cualquier hilo, a diferencia de @WithMockUser,
 * porque fija y restaura el SecurityContext dentro de la propia llamada.
 */
public final class AuthorizedCalls {
  private AuthorizedCalls() {}

  public static <T> T withAuthorities(Supplier<T> call, String... authorities) {
    SecurityContext previous = SecurityContextHolder.getContext();
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(new TestingAuthenticationToken("test-caller", null, authorities));
    SecurityContextHolder.setContext(context);
    try {
      return call.get();
    } finally {
      SecurityContextHolder.setContext(previous);
    }
  }
}
