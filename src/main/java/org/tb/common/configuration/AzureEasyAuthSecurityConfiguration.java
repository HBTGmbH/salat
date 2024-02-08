package org.tb.common.configuration;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.tb.employee.domain.Employee;

@Configuration
@Profile({ "production", "test" })
public class AzureEasyAuthSecurityConfiguration {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
    http.addFilter(preAuthenticatedProcessingFilter(authenticationManager)).authorizeRequests(authz -> authz.antMatchers("/do/**", "**/*.jsp", "/rest/**").authenticated());
    http.csrf().disable();
    return http.build();
  }

  @Bean
  public WebSecurityCustomizer ignoringCustomizer() {
    return (web) -> web.ignoring().antMatchers("*.png", "/images/**", "/style/**", "/scripts/**", "/webjars/**", "/favicon.ico");
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationProvider authenticationProvider, ApplicationEventPublisher publisher) {
    ProviderManager providerManager = new ProviderManager(authenticationProvider);
    providerManager.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(publisher));
    return providerManager;
  }

  private AbstractPreAuthenticatedProcessingFilter preAuthenticatedProcessingFilter(AuthenticationManager authenticationManager) {
    AbstractPreAuthenticatedProcessingFilter preAuthenticatedProcessingFilter = new AbstractPreAuthenticatedProcessingFilter() {
      @Override
      protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
        String claimsBase64 = request.getHeader("x-ms-client-principal");
        String claims = new String(Base64.getDecoder().decode(claimsBase64.getBytes()), Charset.forName("UTF-8"));
        List<String> employeeSigns = JsonPath.read(claims, "$.claims[?(@.typ=='mailnickname')].val");
        String employeeSign = employeeSigns.get(0);
        if(employeeSign == null) {
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
