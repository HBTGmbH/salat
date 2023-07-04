package org.tb.auth;

import com.azure.spring.cloud.autoconfigure.aad.AadWebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class AadOAuth2LoginSecurityConfig extends AadWebSecurityConfigurerAdapter {

  /**
   * Add configuration logic as needed.
   */
  @Override
  protected void configure(HttpSecurity http) throws Exception {
    super.configure(http);
    http
        .authorizeHttpRequests(
            (authorize) -> authorize
                .antMatchers("/swagger-ui.html"
                    , "/swagger-ui/**"
                    , "/v3/**"
                    , "/favicon.ico"
                    //,"/**"
                )
                .permitAll()
                .antMatchers("/**")
                //.hasRole("salat-user")
                //.anyRequest()
                .authenticated()
        );
    // Do some custom configuration.
  }
}
