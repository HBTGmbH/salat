package org.tb.auth.configuration;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.filter.AuthFilter;
import org.tb.auth.filter.AuthViewHelper;
import org.tb.auth.service.AuthService;
import org.tb.common.GlobalConstants;
import org.tb.common.filter.LoggingFilter.MdcDataSource;

@Configuration
@Profile("local")
@RequiredArgsConstructor
public class LocalDevSecurityConfiguration {

  private final AuthorizedUser authorizedUser;
  private final Set<AuthViewHelper> authViewHelpers;
  private final AuthService authService;

  private static final String[] UNAUTHENTICATED_URL_PATTERNS = {
      "/*.png",
      "/images/**",
      "/style/**",
      "/scripts/**",
      "/webjars/**",
      "/favicon.ico",
      "/api/doc/**",
      "/actuator/health",
      "/http-headers*",
      "/error*"
  };

  @Bean
  public FilterRegistrationBean<AuthFilter> authenticationFilter(){
    var registrationBean = new FilterRegistrationBean<AuthFilter>();
    registrationBean.setOrder(101);
    registrationBean.setFilter(new AuthFilter(authorizedUser, authViewHelpers));
    registrationBean.addUrlPatterns("/do/*", "/api/*", "/rest/*", "*.jsp");
    return registrationBean;
  }

  @Bean
  public MdcDataSource authenticationMdcDataSource() {
    return () -> {
      if(authorizedUser.isAuthenticated()) {
        return Map.of("login-sign", authorizedUser.getLoginSign(), "effective-login-sign", authorizedUser.getEffectiveLoginSign());
      }
      return Map.<String, String>of();
    };
  }

  @Bean
  @Order(0)
  SecurityFilterChain resources(HttpSecurity http) throws Exception {
    http.securityMatcher(UNAUTHENTICATED_URL_PATTERNS)
        .authorizeHttpRequests((authorize) -> authorize.anyRequest().permitAll())
        .requestCache().disable()
        .securityContext().disable()
        .sessionManagement().disable()
        .cors().disable()
        .csrf().disable();
    return http.build();
  }

  @Bean
  @Order(1)
  SecurityFilterChain restApi(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
    http.securityMatcher("/api/**", "/rest/**")
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .addFilter(preAuthenticatedProcessingFilter(authenticationManager, false))
        .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
        .requestCache().disable()
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf().disable();
    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
    http.addFilter(preAuthenticatedProcessingFilter(authenticationManager, true))
        .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
        .cors().disable()
        .csrf().disable();
    return http.build();
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
        String loginName = request.getParameter("login-name");
        if(loginName == null && useSession) {
          loginName = (String) request.getSession().getAttribute("login-name");
        } else if(useSession) {
          request.getSession().setAttribute("login-name", loginName);
        }
        return loginName;
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
    provider.setPreAuthenticatedUserDetailsService(token -> {
        String sign = token.getName();
        if (sign == null || sign.isBlank()) {
          throw new UsernameNotFoundException("Missing login-name");
        }
        String status = authService.getStatusByLoginname(sign);
        if (status == null) {
          throw new UsernameNotFoundException("No salat user found for sign: " + sign);
        }
        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        boolean isAdmin = GlobalConstants.EMPLOYEE_STATUS_ADM.equalsIgnoreCase(status);
        boolean isManager = isAdmin || GlobalConstants.EMPLOYEE_STATUS_BL.equalsIgnoreCase(status) || GlobalConstants.EMPLOYEE_STATUS_PV.equalsIgnoreCase(status);
        boolean isBackoffice = isManager || GlobalConstants.EMPLOYEE_STATUS_BO.equalsIgnoreCase(status);
        if (isAdmin) authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        if (isManager) authorities.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
        if (isBackoffice) authorities.add(new SimpleGrantedAuthority("ROLE_BACKOFFICE"));
        return new User(sign, "N/A", authorities);
    });
    return provider;
  }

  private CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("*"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowedMethods(List.of("*"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

}
