package org.tb.common.configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import javax.servlet.http.Cookie;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.HeaderBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@Profile({ "production", "test" })
public class AzureEasyAuthSecurityConfiguration {

  public static final String[] UNAUTHENTICATED_URL_PATTERNS = {
      "*.png",
      "/images/**",
      "/style/**",
      "/scripts/**",
      "/webjars/**",
      "/favicon.ico",
      "/rest/doc/**",
      "/actuator/health",
      "/http-headers*",
      "/error*",
      "/index*",
      "/"
  };

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, SalatProperties salatProperties) throws Exception {
    http.authorizeRequests(authz -> authz.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt())
        .logout(logout -> logout.logoutRequestMatcher(logoutRequestMatcher(salatProperties)).addLogoutHandler(logoutHandler()))
        .csrf().disable();
    return http.build();
  }

  private LogoutHandler logoutHandler() {
    return (request, response, auth) -> {
      Cookie[] cookies = request.getCookies();
      for(Cookie cookie : cookies) {
        cookie.setValue("");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setSecure(true);
        cookie.setComment("EXPIRING COOKIE at " + LocalDateTime.now());
        response.addCookie(cookie);
      }
      try {
        response.sendRedirect("/");
      } catch (IOException e) {
        throw new RuntimeException("Could not send redirect to /", e);
      }
    };
  }

  private RequestMatcher logoutRequestMatcher(SalatProperties salatProperties) {
    // a logout is required if no id token is provided any more or if the access token expires in the next 2 minutes or has been expired
    return (request) -> {
      boolean appServiceAuthSessionPresent = request.getCookies() != null && Arrays.stream(request.getCookies()).anyMatch(c -> c.getName().equalsIgnoreCase("AppServiceAuthSession"));
      boolean oidcTokenPresent = request.getHeader(salatProperties.getAuth().getOidcIdToken().getHeaderName()) != null;
      boolean accessTokenPresent = request.getHeader(salatProperties.getAuth().getAccessToken().getHeaderName()) != null;
      String accessTokenExpiresOn = request.getHeader(salatProperties.getAuth().getAccessToken().getExpiresOnHeaderName());
      OffsetDateTime expires = accessTokenExpiresOn != null ? OffsetDateTime.parse(accessTokenExpiresOn) : OffsetDateTime.MAX;
      boolean accessTokenExpired = accessTokenPresent && expires.isBefore(OffsetDateTime.now().plusMinutes(2)); // grace period of 2 minutes
      return appServiceAuthSessionPresent && (!oidcTokenPresent || accessTokenExpired);
    };
  }

  @Bean
  @Order(0)
  SecurityFilterChain resources(HttpSecurity http) throws Exception {
    http.requestMatchers((matchers) -> matchers.antMatchers(UNAUTHENTICATED_URL_PATTERNS))
        .authorizeHttpRequests((authorize) -> authorize.anyRequest().permitAll())
        .requestCache().disable()
        .securityContext().disable()
        .sessionManagement().disable()
        .csrf().disable();
    return http.build();
  }

  @Bean
  BearerTokenResolver bearerTokenResolver(SalatProperties salatProperties) {
    return new HeaderBearerTokenResolver(salatProperties.getAuth().getOidcIdToken().getHeaderName());
  }

  @Bean
  public JwtAuthenticationConverter customJwtAuthenticationConverter(SalatProperties salatProperties) {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setPrincipalClaimName(salatProperties.getAuth().getOidcIdToken().getPrincipalClaimName());
    return converter;
  }

}
