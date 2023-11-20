package org.tb.auth;

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
import org.springframework.security.web.access.channel.ChannelProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@RequiredArgsConstructor
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Profile({"e2etest"})
public class TestSecurityConfig {

  public final static List<String> EXCLUDE_PATTERN = List.of(
      "/favicon.ico",
      "/error",
      "/**error**",
      "/error.jsp");
  private final List<AntPathRequestMatcher> excludePattern = EXCLUDE_PATTERN.stream()
      .map(s -> new AntPathRequestMatcher(s, null)).toList();
  private final AuthenticationSuccessTestListener authenticationSuccessTestListener;


  /**
   * Add configuration logic as needed.
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http

        //.addFilterAfter(hbtAuthenticationFilter, )
        .authorizeHttpRequests(
            (authorize) -> authorize
                .antMatchers("/**")
                .permitAll()
        )
        .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.ALWAYS).and()
        .addFilterBefore(authenticationSuccessTestListener, ChannelProcessingFilter.class)
        .csrf().disable()
//        .csrf().csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        .build();
  }

  protected boolean shouldNotFilter(HttpServletRequest request) {
    return excludePattern.stream().anyMatch(matcher -> matcher.matches(request));
  }

}
