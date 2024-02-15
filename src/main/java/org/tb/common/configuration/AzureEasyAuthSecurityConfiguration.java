package org.tb.common.configuration;

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
