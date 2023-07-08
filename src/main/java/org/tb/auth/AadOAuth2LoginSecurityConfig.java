package org.tb.auth;

import com.azure.spring.cloud.autoconfigure.aad.AadWebSecurityConfigurerAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@RequiredArgsConstructor
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class AadOAuth2LoginSecurityConfig extends AadWebSecurityConfigurerAdapter {

  private final AuthenticationFilter authenticationFilter;
  /**
   * Add configuration logic as needed.
   */
  @Override
  protected void configure(HttpSecurity http) throws Exception {
    super.configure(http);
    http
        //  .addFilterAfter(authenticationFilter, AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(
            (authorize) -> authorize
                .antMatchers("/swagger-ui.html"
                    , "/swagger-ui/**"
                    , "/v3/**"
                    , "/favicon.ico"
                    , "/error"
                    , "/**error**"
                    , "/error.jsp"
                    //,"/**"
                )
                .permitAll()
                .antMatchers("/**")
                //.hasRole("salat-user")
                //.anyRequest()
                .authenticated()

        )
        .csrf().csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        ;
  }
}
