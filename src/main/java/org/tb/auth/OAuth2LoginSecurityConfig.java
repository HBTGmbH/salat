package org.tb.auth;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@RequiredArgsConstructor
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Profile({"!e2etest"})
public class OAuth2LoginSecurityConfig{

  public final static List<String> EXCLUDE_PATTERN = List.of(
      "/favicon.ico",
      "/error",
      "/**error**",
      "/v3/api-docs",
      "/actuator/*",
      "/error.jsp");
  private final List<AntPathRequestMatcher> excludePattern = EXCLUDE_PATTERN.stream()
      .map(s -> new AntPathRequestMatcher(s, null)).toList();


  /**
   * Add configuration logic as needed.
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return configRest(http)
        .authorizeHttpRequests(
            (authorize) -> authorize
                .antMatchers(EXCLUDE_PATTERN.toArray(new String[0]))
                .permitAll()
                .antMatchers("/**")
                //.hasRole("salat-user")
                //.anyRequest()
                .authenticated()
        )
        .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.ALWAYS).and()
        .oauth2Login(withDefaults())
        .csrf().disable()
        .cors().configurationSource(corsConfigurationSource()).and()
//        .csrf().csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        .build();
  }

  private HttpSecurity configRest(HttpSecurity httpSecurity) throws Exception {
    return httpSecurity

        //.addFilterAfter(hbtAuthenticationFilter, )
        .authorizeHttpRequests(
            (authorize) -> authorize
                .antMatchers(EXCLUDE_PATTERN.toArray(new String[0]))
                .permitAll()
                .antMatchers("/rest/**")
                .authenticated()
        )
        .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.NEVER).and()
        .oauth2ResourceServer(oauth2 -> oauth2.jwt())
        .csrf().disable()
        .cors().configurationSource(corsConfigurationSource()).and();
  }

  private CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.applyPermitDefaultValues();
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/rest/**", configuration);
    return source;
  }

  protected boolean shouldNotFilter(HttpServletRequest request) {
    return excludePattern.stream().anyMatch(matcher -> matcher.matches(request));
  }
}
