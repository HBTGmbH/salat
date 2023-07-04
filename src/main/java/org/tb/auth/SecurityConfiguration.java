//package org.tb.auth;
//
//import static org.springframework.security.config.Customizer.withDefaults;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
//import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
//import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
//import org.tb.common.configuration.AuthenticationFilter;
//
//@Configuration
//@EnableWebSecurity
//@RequiredArgsConstructor
//public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
//
//  private final AuthenticationFilter authenticationFilter;
//  @Override
//  protected void configure(HttpSecurity http) throws Exception {
//    http
////        .authorizeHttpRequests(
////            (authorize) -> authorize
////                .antMatchers("/swagger-ui.html"
////                    , "/swagger-ui/**"
////                    , "/v3/**"
////                    , "/favicon.ico"
////                    //,"/**"
////                )
////                .permitAll()
////                .antMatchers("/**")
//////                .hasRole("salat-user")
//////                .anyRequest()
////                .authenticated())
////        .oauth2Login(withDefaults())
//        .addFilterAfter(
//            authenticationFilter, OAuth2LoginAuthenticationFilter.class)
//    // ... endpoints
////        .formLogin()
////        .loginPage("/login.html")
////        .loginProcessingUrl("/login")
////        .defaultSuccessUrl("/homepage.html", true)
//    ;
//    // ... other configuration
//  }
//}
