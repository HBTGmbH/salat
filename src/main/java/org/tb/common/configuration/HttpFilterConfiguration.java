package org.tb.common.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter;
import org.tb.auth.AuthorizedUser;
import org.tb.employee.EmployeeRepository;

@Configuration
@RequiredArgsConstructor
public class HttpFilterConfiguration {

    private final AuthorizedUser authorizedUser;
    private final EmployeeRepository employeeRepository;

    @Bean
    public FilterRegistrationBean<AuthenticationFilter> authenticationFilter(){
        var registrationBean = new FilterRegistrationBean<AuthenticationFilter>();
        registrationBean.setFilter(new AuthenticationFilter(authorizedUser, employeeRepository));
        registrationBean.addUrlPatterns("/do/*", "/rest/*", "*.jsp");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<PerformanceLoggingFilter> performanceLoggingFilter(){
        var registrationBean = new FilterRegistrationBean<PerformanceLoggingFilter>();
        registrationBean.setFilter(new PerformanceLoggingFilter());
        registrationBean.addUrlPatterns("/do/*", "/rest/*", "*.jsp");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<OpenEntityManagerInViewFilter> openEntityManagerInViewFilter(){
        var registrationBean = new FilterRegistrationBean<OpenEntityManagerInViewFilter>();
        registrationBean.setFilter(new OpenEntityManagerInViewFilter());
        registrationBean.addUrlPatterns("/do/*");
        return registrationBean;
    }

}
