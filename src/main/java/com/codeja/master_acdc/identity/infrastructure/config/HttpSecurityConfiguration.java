package com.codeja.master_acdc.identity.infrastructure.config;

import com.codeja.master_acdc.identity.infrastructure.adapters.in.web.SecurityProblems;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class HttpSecurityConfiguration {
  @Bean
  public SecurityProblems securityProblems(ObjectMapper json) {
    return new SecurityProblems(json);
  }

  @Bean
  public CookieCsrfTokenRepository csrfTokenRepository() {
    var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookieCustomizer(cookie -> cookie.path("/").secure(true).sameSite("Strict"));
    return repository;
  }

  @Bean
  @Order(1)
  public SecurityFilterChain actuatorSecurity(
      HttpSecurity http,
      SecurityProblems problems,
      @Value("${shop.ops.user}") String user,
      @Value("${shop.ops.password-hash}") String hash)
      throws Exception {
    var users =
        new InMemoryUserDetailsManager(User.withUsername(user).password(hash).roles("OPS").build());
    var provider = new DaoAuthenticationProvider(users);
    provider.setPasswordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder());
    var basic = new BasicAuthenticationEntryPoint();
    basic.setRealmName("operations");
    basic.afterPropertiesSet();
    var entryPoint = problems.entryPoint(basic);
    http.securityMatcher(EndpointRequest.toAnyEndpoint())
        .authenticationProvider(provider)
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(EndpointRequest.to("health"))
                    .permitAll()
                    .anyRequest()
                    .hasRole("OPS"))
        .httpBasic(auth -> auth.authenticationEntryPoint(entryPoint))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(problems.deniedHandler()));
    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain jwksSecurity(HttpSecurity http, SecurityProblems problems)
      throws Exception {
    http.securityMatcher("/.well-known/jwks.json")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/.well-known/jwks.json")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            errors ->
                errors
                    .accessDeniedHandler(problems.deniedHandler())
                    .authenticationEntryPoint(
                        (request, response, failure) ->
                            problems
                                .deniedHandler()
                                .handle(
                                    request,
                                    response,
                                    new org.springframework.security.access.AccessDeniedException(
                                        "forbidden"))));
    return http.build();
  }

  @Bean
  @Order(3)
  public SecurityFilterChain apiSecurity(
      HttpSecurity http, SecurityProblems problems, CookieCsrfTokenRepository csrf)
      throws Exception {
    var entryPoint = problems.entryPoint(new BearerTokenAuthenticationEntryPoint());
    var denied = problems.deniedHandler();
    http.securityMatcher("/api/**")
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .csrf(
            protection ->
                protection
                    .csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .requireCsrfProtectionMatcher(cookieAuthenticationRequests()))
        .exceptionHandling(
            errors -> errors.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
        .oauth2ResourceServer(
            resource ->
                resource
                    .jwt(jwt -> {})
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(denied));
    return http.build();
  }

  private RequestMatcher cookieAuthenticationRequests() {
    return new OrRequestMatcher(
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/refresh"),
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/logout"));
  }
}
