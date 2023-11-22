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

@RequiredArgsConstructor
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Profile({"production"})
public class OAuth2LoginSecurityConfig {

  public final static List<String> EXCLUDE_PATTERN = List.of(
      "/favicon.ico",
      "/error",
      "/**error**",
      "/error.jsp");
  private final List<AntPathRequestMatcher> excludePattern = EXCLUDE_PATTERN.stream()
      .map(s -> new AntPathRequestMatcher(s, null)).toList();


  /**
   * Add configuration logic as needed.
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http

        //.addFilterAfter(hbtAuthenticationFilter, )
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
        .cors()
        .and()
//        .antMatchers(HttpMethod.GET, "/user/info", "/api/foos/**")
//        .hasAuthority("SCOPE_read")
//        .antMatchers(HttpMethod.POST, "/api/foos")
//        .hasAuthority("SCOPE_write")
        .csrf().disable()
//        .csrf().csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        .build();
  }

  protected boolean shouldNotFilter(HttpServletRequest request) {
    return excludePattern.stream().anyMatch(matcher -> matcher.matches(request));
  }
}
