package org.tb.common.configuration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.tb.employee.domain.Employee;

@Configuration
@Profile("local")
public class LocalDevSecurityConfiguration {

  public static final String[] UNAUTHENTICATED_URL_PATTERNS = {
      "/*.png",
      "/images/**",
      "/style/**",
      "/scripts/**",
      "/webjars/**",
      "/favicon.ico",
      "/rest/doc/**",
      "/actuator/health",
      "/error"
  };

  @Bean
  @Order(0)
  SecurityFilterChain resources(HttpSecurity http) throws Exception {
    http
        .securityMatcher(UNAUTHENTICATED_URL_PATTERNS)
        .authorizeHttpRequests((authorize) -> authorize.anyRequest().permitAll())
        .requestCache().disable()
        .securityContext().disable()
        .sessionManagement().disable()
        .csrf().disable();
    return http.build();
  }

  @Bean
  @Order(1)
  SecurityFilterChain restApi(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
    http.securityMatcher("/rest/**")
        .addFilter(preAuthenticatedProcessingFilter(authenticationManager, false))
        .authorizeRequests(authz -> authz.anyRequest().authenticated())
        .logout(logout -> logout.logoutRequestMatcher(logoutRequestMatcher()).addLogoutHandler(logoutHandler()))
        .requestCache().disable()
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf().disable();
    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
    http.addFilter(preAuthenticatedProcessingFilter(authenticationManager, true))
        .authorizeRequests(authz -> authz.anyRequest().authenticated())
        .logout(logout -> logout.logoutRequestMatcher(logoutRequestMatcher()).addLogoutHandler(logoutHandler()))
        .csrf().disable();
    return http.build();
  }

  private LogoutHandler logoutHandler() {
    return (request, response, auth) -> {
      Cookie[] cookies = request.getCookies();
      for(Cookie cookie : cookies) {
        cookie.setValue("");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setSecure(true);
        cookie.setComment("EXPIRING COOKIE at " + LocalDateTime.now());
        response.addCookie(cookie);
      }
      try {
        response.sendRedirect("/");
      } catch (IOException e) {
        throw new RuntimeException("Could not send redirect to /", e);
      }
    };
  }

  private RequestMatcher logoutRequestMatcher() {
    return (request) -> request.getParameter("logout") != null;
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationProvider authenticationProvider, ApplicationEventPublisher publisher) {
    ProviderManager providerManager = new ProviderManager(authenticationProvider);
    providerManager.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(publisher));
    return providerManager;
  }

  private AbstractPreAuthenticatedProcessingFilter preAuthenticatedProcessingFilter(AuthenticationManager authenticationManager, boolean useSession) {
    AbstractPreAuthenticatedProcessingFilter preAuthenticatedProcessingFilter = new AbstractPreAuthenticatedProcessingFilter() {
      @Override
      protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
        String employeeSign = request.getParameter("employee-sign");
        if(employeeSign == null && useSession) {
          Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
          if(loginEmployee != null) {
            employeeSign = loginEmployee.getSign();
          }
        }
        return employeeSign;
      }

      @Override
      protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
        return "N/A";
      }
    };
    preAuthenticatedProcessingFilter.setAuthenticationManager(authenticationManager);
    preAuthenticatedProcessingFilter.setCheckForPrincipalChanges(true);
    return preAuthenticatedProcessingFilter;
  }

  @Bean
  public AuthenticationProvider authenticationProvider() {
    PreAuthenticatedAuthenticationProvider provider = new PreAuthenticatedAuthenticationProvider();
    provider.setPreAuthenticatedUserDetailsService(new AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken>() {
      @Override
      public UserDetails loadUserDetails(PreAuthenticatedAuthenticationToken token) throws UsernameNotFoundException {
        return new User(token.getName(), "N/A", Set.of());
      }
    });
    return provider;
  }

}
