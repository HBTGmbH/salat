package org.tb.settings.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.tb.settings.web.LocaleSyncInterceptor;

@Configuration
@RequiredArgsConstructor
public class SettingsMvcConfiguration implements WebMvcConfigurer {

    private final LocaleSyncInterceptor localeSyncInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeSyncInterceptor);
    }

}
