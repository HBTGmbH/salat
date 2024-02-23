package org.tb.common.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter;
import org.springframework.web.servlet.resource.ResourceUrlEncodingFilter;
import org.springframework.web.servlet.resource.ResourceUrlProvider;
import org.tb.auth.AuthViewHelper;
import org.tb.auth.AuthorizedUser;
import org.tb.common.filter.AuthenticationFilter;
import org.tb.common.filter.PerformanceLoggingFilter;
import org.tb.common.filter.ResourceUrlProviderExposingFilter;
import org.tb.employee.persistence.EmployeeRepository;

@Configuration
@RequiredArgsConstructor
public class HttpFilterConfiguration {

    private final AuthViewHelper authViewHelper;
    private final AuthorizedUser authorizedUser;
    private final EmployeeRepository employeeRepository;
    private final ResourceUrlProvider resourceUrlProvider;

    @Bean
    public FilterRegistrationBean<ResourceUrlEncodingFilter> ResourceUrlEncodingFilter(){
        var registrationBean = new FilterRegistrationBean<ResourceUrlEncodingFilter>();
        registrationBean.setOrder(97);
        registrationBean.setFilter(new ResourceUrlEncodingFilter());
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<ResourceUrlProviderExposingFilter> ResourceUrlProviderExposingFilter(){
        var registrationBean = new FilterRegistrationBean<ResourceUrlProviderExposingFilter>();
        registrationBean.setOrder(98);
        registrationBean.setFilter(new ResourceUrlProviderExposingFilter(resourceUrlProvider));
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<PerformanceLoggingFilter> performanceLoggingFilter(){
        var registrationBean = new FilterRegistrationBean<PerformanceLoggingFilter>();
        registrationBean.setOrder(99);
        registrationBean.setFilter(new PerformanceLoggingFilter());
        registrationBean.addUrlPatterns("/do/*", "/rest/*", "*.jsp");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<OpenEntityManagerInViewFilter> openEntityManagerInViewFilter(){
        var registrationBean = new FilterRegistrationBean<OpenEntityManagerInViewFilter>();
        registrationBean.setOrder(100);
        registrationBean.setFilter(new OpenEntityManagerInViewFilter());
        registrationBean.addUrlPatterns("/do/*", "/rest/*", "*.jsp");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<AuthenticationFilter> authenticationFilter(){
        var registrationBean = new FilterRegistrationBean<AuthenticationFilter>();
        registrationBean.setOrder(101);
        registrationBean.setFilter(new AuthenticationFilter(authViewHelper, authorizedUser, employeeRepository));
        registrationBean.addUrlPatterns("/do/*", "/rest/*", "*.jsp");
        return registrationBean;
    }

}
