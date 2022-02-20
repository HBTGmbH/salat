package org.tb.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tb.bdom.AuthorizedUser;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeeRepository;

@Configuration
@RequiredArgsConstructor
public class HttpFilterConfiguration {

    private final AuthorizedUser authorizedUser;
    private final EmployeeRepository employeeRepository;

    @Bean
    public FilterRegistrationBean<AuthenticationFilter> authenticationFilter(){
        var registrationBean = new FilterRegistrationBean<AuthenticationFilter>();
        registrationBean.setFilter(new AuthenticationFilter(authorizedUser, employeeRepository));
        registrationBean.addUrlPatterns("/do/*", "/rest/*");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<PerformanceLoggingFilter> performanceLoggingFilter(){
        var registrationBean = new FilterRegistrationBean<PerformanceLoggingFilter>();
        registrationBean.setFilter(new PerformanceLoggingFilter());
        registrationBean.addUrlPatterns("/do/*", "/rest/*", "*.jsp");
        return registrationBean;
    }

}
