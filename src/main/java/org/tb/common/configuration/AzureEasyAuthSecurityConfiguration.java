package org.tb.common.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.HeaderBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile({ "production", "test" })
public class AzureEasyAuthSecurityConfiguration {

  @Value("${salat.auth.id-token.header-name}")
  private String idTokenHeaderName;

  @Value("${salat.auth.id-token.principal-claim-name}")
  private String idTokenPrinciplaClaimName;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.authorizeRequests(authz -> authz.antMatchers("/do/**", "**/*.jsp", "/rest/**").authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt());
    http.csrf().disable();
    return http.build();
  }

  @Bean
  public WebSecurityCustomizer ignoringCustomizer() {
    return (web) -> web.ignoring().antMatchers("*.png", "/images/**", "/style/**", "/scripts/**", "/webjars/**", "/favicon.ico", "/rest/doc/**");
  }

  @Bean
  BearerTokenResolver bearerTokenResolver() {
    HeaderBearerTokenResolver bearerTokenResolver = new HeaderBearerTokenResolver(idTokenHeaderName);
    return bearerTokenResolver;
  }

  @Bean
  public JwtAuthenticationConverter customJwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setPrincipalClaimName(idTokenPrinciplaClaimName);
    return converter;
  }

}
