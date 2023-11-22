package org.tb.auth;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@RequiredArgsConstructor
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Profile({"!e2etest"})
@Slf4j
public class OAuth2LoginSecurityConfig{

  public final static String[] EXCLUDE_PATTERN = List.of(
      "/favicon.ico",
      "/error",
      "/**error**",
      "/v3/api-docs",
      "/actuator/*",
      "/error.jsp")
      .toArray(new String[0]);

  private final CorsRestConfiguration corsRestConfiguration;

  /**
   * Add configuration logic as needed.
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http = configRest(http);
    http = configLegacySalat(http);
    return http.build();
  }

  private HttpSecurity configLegacySalat(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(
            (authorize) -> authorize
                .antMatchers(EXCLUDE_PATTERN)
                .permitAll()
                .antMatchers("/**")
                .authenticated()
        )
        .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.ALWAYS).and()
        .oauth2Login(withDefaults())
        .csrf().disable();
  }

  private HttpSecurity configRest(HttpSecurity httpSecurity) throws Exception {
    return httpSecurity
        .authorizeHttpRequests(
            (authorize) -> authorize
                .antMatchers(EXCLUDE_PATTERN)
                .permitAll()
                .antMatchers("/rest/**")
                .hasAuthority("SCOPE_user_impersonation")
        )
        .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.NEVER).and()
        .oauth2ResourceServer(oauth2 -> oauth2.jwt())
        .csrf().disable()
        ;
  }

  @Bean
  public FilterRegistrationBean corsRestFilterBean() {
    final CorsConfiguration config = new CorsConfiguration();

    config.setAllowCredentials(true);

    if (corsRestConfiguration.getAllowedOrigins() != null) {
      corsRestConfiguration.getAllowedOrigins().forEach(config::addAllowedOrigin);
    } else {
      log.error("property salat.rest.cors.allowed-origins not set. CORS won't work!");
    }
    config.addAllowedHeader("*");
    config.addAllowedMethod("*");

    final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/rest/**", config);

    FilterRegistrationBean bean = new FilterRegistrationBean(new CorsFilter(source));
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);

    return bean;
  }

}
