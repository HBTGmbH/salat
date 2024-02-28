package org.tb.common.configuration;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter;
import org.springframework.web.servlet.resource.ResourceUrlProvider;
import org.tb.auth.AuthViewHelper;
import org.tb.auth.AuthorizedUser;
import org.tb.common.SalatProperties;
import org.tb.common.filter.AuthenticationFilter;
import org.tb.common.filter.LoggingFilter;
import org.tb.common.filter.LoggingFilter.MdcDataSource;
import org.tb.common.filter.PerformanceLoggingFilter;
import org.tb.common.filter.ResourceUrlEncodingFilter;
import org.tb.common.filter.ResourceUrlProviderExposingFilter;

@Configuration
@RequiredArgsConstructor
public class HttpConfiguration {

    private final Set<MdcDataSource> mdcDataSources;
    private final ResourceUrlProvider resourceUrlProvider;
    private final SalatProperties salatProperties;

    @Bean
    public FilterRegistrationBean<ResourceUrlEncodingFilter> resourceUrlEncodingFilter(){
        var registrationBean = new FilterRegistrationBean<ResourceUrlEncodingFilter>();
        registrationBean.setOrder(97);
        registrationBean.setFilter(new ResourceUrlEncodingFilter());
        registrationBean.addUrlPatterns("/do/*", "*.jsp");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<ResourceUrlProviderExposingFilter> resourceUrlProviderExposingFilter(){
        var registrationBean = new FilterRegistrationBean<ResourceUrlProviderExposingFilter>();
        registrationBean.setOrder(98);
        registrationBean.setFilter(new ResourceUrlProviderExposingFilter(resourceUrlProvider));
        registrationBean.addUrlPatterns("/do/*", "*.jsp");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<PerformanceLoggingFilter> performanceLoggingFilter(){
        var registrationBean = new FilterRegistrationBean<PerformanceLoggingFilter>();
        registrationBean.setOrder(99);
        registrationBean.setFilter(new PerformanceLoggingFilter());
        registrationBean.addUrlPatterns("/do/*", "/api/*", "/rest/*", "*.jsp");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<OpenEntityManagerInViewFilter> openEntityManagerInViewFilter(){
        var registrationBean = new FilterRegistrationBean<OpenEntityManagerInViewFilter>();
        registrationBean.setOrder(100);
        registrationBean.setFilter(new OpenEntityManagerInViewFilter());
        registrationBean.addUrlPatterns("/do/*", "/api/*", "/rest/*", "*.jsp");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<LoggingFilter> loggingFilter(){
        var registrationBean = new FilterRegistrationBean<LoggingFilter>();
        registrationBean.setOrder(102);
        registrationBean.setFilter(new LoggingFilter(mdcDataSources));
        registrationBean.addUrlPatterns("/do/*", "/api/*", "/rest/*", "*.jsp");
        return registrationBean;
    }

    @Bean
    public ServletContextInitializer addComponentsToServletContext() {
        return servletContext -> {
            servletContext.setAttribute("salatProperties", salatProperties);
        };
    }

}
