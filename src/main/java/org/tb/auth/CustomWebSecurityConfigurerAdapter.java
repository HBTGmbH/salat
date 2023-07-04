//package org.tb.auth;
//
//import static org.springframework.security.config.Customizer.withDefaults;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
//import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
//import org.springframework.security.web.SecurityFilterChain;
//import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
//import org.tb.common.configuration.AuthenticationFilter;
//
//@Configuration
//public class CustomWebSecurityConfigurerAdapter {
//
//  private static final String JWT_ROLE_NAME = "roles";
//  private static final String ROLE_PREFIX = "ROLE_";
//
//  //
//  @Bean
//  public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationFilter authenticationFilter)
//      throws Exception {
//
////    super.configure(http);
//    http
//        .addFilterAfter(
//            authenticationFilter, BasicAuthenticationFilter.class)
//        .authorizeHttpRequests(
//            (authorize) -> authorize
//                .antMatchers("/swagger-ui.html"
//                    , "/swagger-ui/**"
//                    , "/v3/**"
//                    , "/favicon.ico"
//                    //,"/**"
//                )
//                .permitAll()
//                .antMatchers("/**")
////                //.hasRole("salat-user")
////                //.anyRequest()
//                .authenticated()
//        )
//        .oauth2Login(withDefaults())
////        .oauth2ResourceServer(oauth2 -> oauth2.jwt(
////            jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
//////        .sessionManagement(
//////            session -> session.sessionCreationPolicy(SessionCreationPolicy.ALWAYS))
//////        .csrf()
//////        .disable();
////////        .exceptionHandling((exceptions) -> exceptions
////////            .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
////////            .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
////////        );
////
////  }
////
////
//        .anonymous().disable()
//
//    ;
//    return http.build();
//  }
//
//  /*
//  create a custom JWT converter to map the roles from the token as granted authorities
//   */
//  private JwtAuthenticationConverter jwtAuthenticationConverter() {
//    JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
//    jwtGrantedAuthoritiesConverter.setAuthoritiesClaimName(JWT_ROLE_NAME); // default is: scope, scp
//    jwtGrantedAuthoritiesConverter.setAuthorityPrefix(ROLE_PREFIX); // default is: SCOPE_
//
//    JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
//    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
//    return jwtAuthenticationConverter;
//  }
//}