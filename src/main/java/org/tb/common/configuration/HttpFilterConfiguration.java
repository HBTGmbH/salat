package org.tb.common.configuration;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter;
import org.tb.common.filter.LoggingFilter;
import org.tb.common.filter.LoggingFilter.MdcDataSource;
import org.tb.common.filter.PerformanceLoggingFilter;
import org.tb.common.filter.UiStateFilter;
import org.tb.common.web.UiState;
import org.tb.common.web.UiStateKeyRegistry;

@Configuration
@RequiredArgsConstructor
public class HttpFilterConfiguration {

    private final Set<MdcDataSource> mdcDataSources;
    private final UiState uiState;
    private final UiStateKeyRegistry uiStateKeyRegistry;

    @Bean
    public FilterRegistrationBean<PerformanceLoggingFilter> performanceLoggingFilter(){
        var registrationBean = new FilterRegistrationBean<PerformanceLoggingFilter>();
        registrationBean.setOrder(99);
        registrationBean.setFilter(new PerformanceLoggingFilter());
        registrationBean.addUrlPatterns("*");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<OpenEntityManagerInViewFilter> openEntityManagerInViewFilter(){
        var registrationBean = new FilterRegistrationBean<OpenEntityManagerInViewFilter>();
        registrationBean.setOrder(100);
        registrationBean.setFilter(new OpenEntityManagerInViewFilter());
        registrationBean.addUrlPatterns("*");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<LoggingFilter> loggingFilter(){
        var registrationBean = new FilterRegistrationBean<LoggingFilter>();
        registrationBean.setOrder(102);
        registrationBean.setFilter(new LoggingFilter(mdcDataSources));
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<UiStateFilter> uiStateFilter() {
        var registrationBean = new FilterRegistrationBean<UiStateFilter>();
        registrationBean.setOrder(103);
        registrationBean.setFilter(new UiStateFilter(uiState, uiStateKeyRegistry));
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }

}
