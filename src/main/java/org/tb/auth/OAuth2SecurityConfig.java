package org.tb.auth;

import static org.springframework.security.config.Customizer.withDefaults;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@RequiredArgsConstructor
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@ConditionalOnExpression("${salat.oauth.enabled}")
@Slf4j
public class OAuth2SecurityConfig {

  public final static String[] EXCLUDE_PATTERN = List.of(
      "/favicon.ico",
      "/error",
      "/**error**",
      "/rest/doc/api-docs",
      "/actuator/*",
      "/error.jsp")
      .toArray(new String[0]);

  @Value("${salat.oauth.logout.url}")
  private String logoutUrl;

  /**
   * Add configuration logic as needed.
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http = configRest(http);
    http = configLegacySalat(http);
    http = http.logout()
        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
        .addLogoutHandler(redirectToAdLogout())
        .deleteCookies("JSESSIONID")
        .invalidateHttpSession(true).and();
    return http.build();
  }


  private LogoutHandler redirectToAdLogout() {
    return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
      String currentUrl = request.getRequestURL().toString();
      String redirectUrl = currentUrl.substring(0, currentUrl.length() - request.getRequestURI().length()) + "/";
      String fullRedirectUrl = logoutUrl + "?post_logout_redirect_uri=" + URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8);
      try {
        response.sendRedirect(fullRedirectUrl);
      } catch (IOException e) {
        log.error("Could not redirect to {}", fullRedirectUrl, e);
      }
    };
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

}
