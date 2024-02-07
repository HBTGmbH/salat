package org.tb.common.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.HeaderBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AzureEasyAuthConfiguration {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.authorizeRequests(authz -> authz.antMatchers("/do/**", "**/*.jsp", "/rest/**").authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt());
    return http.build();
  }

  @Bean
  public WebSecurityCustomizer ignoringCustomizer() {
    return (web) -> web.ignoring().antMatchers("*.png", "/images/**", "/style/**", "/scripts/**", "/webjars/**", "/favicon.ico");
  }

  @Bean
  BearerTokenResolver bearerTokenResolver() {
    // TODO move to application.yml
    HeaderBearerTokenResolver bearerTokenResolver = new HeaderBearerTokenResolver("x-ms-token-aad-id-token");
    return bearerTokenResolver;
  }

  @Bean
  public JwtAuthenticationConverter customJwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setPrincipalClaimName("mailnickname");
    return converter;
  }

}
