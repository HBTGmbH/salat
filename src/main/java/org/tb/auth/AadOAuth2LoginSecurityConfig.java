package org.tb.auth;

import com.azure.spring.cloud.autoconfigure.aad.AadWebSecurityConfigurerAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@RequiredArgsConstructor
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Profile({"test","prod","local"})
public class AadOAuth2LoginSecurityConfig extends AadWebSecurityConfigurerAdapter {

  final private HbtAuthenticationFilter hbtAuthenticationFilter;

  /**
   * Add configuration logic as needed.
   */
  @Override
  protected void configure(HttpSecurity http) throws Exception {
    super.configure(http);
    http
        //.addFilterAfter(hbtAuthenticationFilter, )
        .authorizeHttpRequests(
            (authorize) -> authorize
                .antMatchers(HbtAuthenticationFilter.EXCLUDE_PATTERN.toArray(new String[0]))
                .permitAll()
                .antMatchers("/**")
                //.hasRole("salat-user")
                //.anyRequest()
                .authenticated()
        )
        .csrf().disable()
//        .csrf().csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        ;
  }
}
