package org.tb.auth.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.tb.auth.web.ImpersonationSecurityInterceptor;

@Configuration
@RequiredArgsConstructor
public class AuthMvcConfiguration implements WebMvcConfigurer {

    private final ImpersonationSecurityInterceptor impersonationSecurityInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(impersonationSecurityInterceptor);
    }
}
