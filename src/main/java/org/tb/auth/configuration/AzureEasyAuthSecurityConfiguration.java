package org.tb.auth.configuration;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.util.Assert;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.domain.EmployeeStatusAuthorities;
import org.tb.auth.service.AuthService;
import org.tb.common.SalatProperties;
import org.tb.common.filter.LoggingFilter.MdcDataSource;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@Profile({ "production", "staging", "localeasyauth" })
@RequiredArgsConstructor
@Slf4j
public class AzureEasyAuthSecurityConfiguration {

  private final AuthorizedUser authorizedUser;
  private final ObjectFactory<HttpServletRequest> requestProvider;

  private static final String[] UNAUTHENTICATED_URL_PATTERNS = {
      "/*.png",
      "/images/**",
      "/style/**",
      "/scripts/**",
      "/webjars/**",
      "/favicon.ico",
      "/api/doc/**",
      "/actuator/health"
  };

  @Bean
  public MdcDataSource authenticationMdcDataSource() {
    return () -> {
      if(authorizedUser.isAuthenticated()) {
        return Map.of("login-sign", authorizedUser.getLoginSign(), "effective-login-sign", authorizedUser.getEffectiveLoginSign());
      }
      return Map.of();
    };
  }

  @Bean
  @Order(0)
  SecurityFilterChain resources(HttpSecurity http) throws Exception {
    http.securityMatcher(UNAUTHENTICATED_URL_PATTERNS)
        .authorizeHttpRequests((authorize) -> authorize.anyRequest().permitAll())
        .requestCache(RequestCacheConfigurer::disable)
        .securityContext(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(AbstractHttpConfigurer::disable)
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }

  @Bean
  @Order(1)
  SecurityFilterChain restApi(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
    http.securityMatcher("/api/**", "/rest/**")
        .authorizeHttpRequests((authorize) -> authorize.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .requestCache(RequestCacheConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      JwtAuthenticationConverter jwtAuthenticationConverter
  ) {
    http.authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .cors(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
        .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
    ;
    return http.build();
  }

  @Bean
  BearerTokenResolver bearerTokenResolver(SalatProperties salatProperties) {
    return new MultiHeaderBearerTokenResolver(
        salatProperties.getAuth().getEasyAuth().getOidcIdToken().getHeaderName(),
        "Authorization"
    );
  }

  @Bean
  public JwtAuthenticationConverter customJwtAuthenticationConverter(SalatProperties salatProperties, AuthService authService) {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    String principalClaim = salatProperties.getAuth().getEasyAuth().getOidcIdToken().getPrincipalClaimName();
    converter.setPrincipalClaimName(principalClaim);
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
      String sign = jwt.getClaimAsString(principalClaim);
      if (sign == null || sign.isBlank()) {
        throw new UsernameNotFoundException("Missing principal claim: " + principalClaim);
      }
      String status = authService.getStatusByLoginname(sign);
      if (status == null) {
        throw new UsernameNotFoundException("No salat user found for sign: " + sign);
      }
      return EmployeeStatusAuthorities.from(status);
    });
    return converter;
  }

  @Bean
  JwtDecoder jwtDecoder(SalatProperties salatProperties, OAuth2ResourceServerProperties resourceServerProperties) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(resourceServerProperties.getJwt().getIssuerUri()).build();

    // validate oid claim against easy auth header
    String principalIdClaimName = salatProperties.getAuth().getEasyAuth().getOidcIdToken().getPrincipalIdClaimName();
    var easyAuthPrincipalValidator = new JwtClaimValidator<>(principalIdClaimName, value -> {
      String principalIdHeaderName = salatProperties.getAuth().getEasyAuth().getPrincipalIdHeaderName();
      String clientPrincipalId = requestProvider.getObject().getHeader(principalIdHeaderName);
      boolean valid = value != null && value.equals(clientPrincipalId);
      if(!valid) {
        log.warn(
            "oid claim in jwt does not match easy auth header. jwt: {}={}, easy auth: {}={}",
            principalIdClaimName, value, principalIdHeaderName, clientPrincipalId
        );
      }
      return valid;
    });

    // skip expiration validations
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator(JwtTypeValidator.jwt(), easyAuthPrincipalValidator));
    return decoder;
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
