package org.tb.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RestCorsConfiguration {

  private final CorsRestConfiguration corsRestConfiguration;

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
