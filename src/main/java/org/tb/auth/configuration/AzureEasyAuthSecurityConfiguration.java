package org.tb.auth.configuration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.filter.AuthFilter;
import org.tb.auth.filter.AuthViewHelper;
import org.tb.common.SalatProperties;
import org.tb.common.filter.LoggingFilter.MdcDataSource;

@Configuration
@Profile({ "production", "staging" })
@RequiredArgsConstructor
public class AzureEasyAuthSecurityConfiguration {

  private final AuthorizedUser authorizedUser;
  private final Set<AuthViewHelper> authViewHelpers;

  private static final String[] UNAUTHENTICATED_URL_PATTERNS = {
      "/*.png",
      "/images/**",
      "/style/**",
      "/scripts/**",
      "/webjars/**",
      "/favicon.ico",
      "/api/doc/**",
      "/actuator/health",
      "/http-headers*",
      "/error*"
  };

  @Bean
  public FilterRegistrationBean<AuthFilter> authenticationFilter(){
    var registrationBean = new FilterRegistrationBean<AuthFilter>();
    registrationBean.setOrder(101);
    registrationBean.setFilter(new AuthFilter(authorizedUser, authViewHelpers));
    registrationBean.addUrlPatterns("/do/*", "/api/*", "/rest/*", "*.jsp");
    return registrationBean;
  }

  @Bean
  public MdcDataSource authenticationMdcDataSource() {
    return () -> {
      if(authorizedUser.isAuthenticated()) {
        return Map.of("login-sign", authorizedUser.getLoginSign(), "user-sign", authorizedUser.getSign());
      }
      return Map.of();
    };
  }

  @Bean
  @Order(0)
  SecurityFilterChain resources(HttpSecurity http) throws Exception {
    http.securityMatcher(UNAUTHENTICATED_URL_PATTERNS)
        .authorizeHttpRequests((authorize) -> authorize.anyRequest().permitAll())
        .requestCache().disable()
        .securityContext().disable()
        .sessionManagement().disable()
        .cors().disable()
        .csrf().disable();
    return http.build();
  }

  @Bean
  @Order(1)
  SecurityFilterChain restApi(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/**", "/rest/**")
        .authorizeHttpRequests((authorize) -> authorize.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt())
        .requestCache().disable()
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf().disable();
    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain filterChain(HttpSecurity http, SalatProperties salatProperties) throws Exception {
    http.authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt())
        .logout(logout -> logout.logoutRequestMatcher(logoutRequestMatcher(salatProperties)).addLogoutHandler(logoutHandler()))
        .cors().disable()
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
  BearerTokenResolver bearerTokenResolver(SalatProperties salatProperties) {
    return new MultiHeaderBearerTokenResolver(
        salatProperties.getAuth().getOidcIdToken().getHeaderName(),
        "Authorization"
    );
  }

  @Bean
  public JwtAuthenticationConverter customJwtAuthenticationConverter(SalatProperties salatProperties) {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setPrincipalClaimName(salatProperties.getAuth().getOidcIdToken().getPrincipalClaimName());
    return converter;
  }

  public static class MultiHeaderBearerTokenResolver implements BearerTokenResolver {

    private String[] headers;

    public MultiHeaderBearerTokenResolver(String... headers) {
      this.headers = headers;
      for(String header : headers) {
        Assert.hasText(header, "header cannot be empty");
      }
    }

    @Override
    public String resolve(HttpServletRequest request) {
      for(String header : headers) {
        String value = request.getHeader(header);
        if(value != null) {
          if(value.startsWith("Bearer ")) {
            value = value.substring("Bearer ".length());
          }
          return value;
        }
      }
      return null;
    }

  }

  private CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("*"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowedMethods(List.of("*"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

}
