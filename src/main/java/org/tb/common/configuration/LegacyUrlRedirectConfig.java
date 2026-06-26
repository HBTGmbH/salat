package org.tb.common.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class LegacyUrlRedirectConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        redirect(registry, "/", "/dailyreport/dashboard");
        redirect(registry, "/welcome", "/dailyreport/dashboard");
        redirect(registry, "/do/ShowDailyReport", "/dailyreport/daily");
        redirect(registry, "/do/ShowMatrix", "/dailyreport/matrix");
        redirect(registry, "/do/CreateDailyReport", "/dailyreport/timereports/new");
        redirect(registry, "/dailyreport/overtime", "/my-accounts");
    }

    private static void redirect(ViewControllerRegistry registry, String from, String to) {
        registry.addRedirectViewController(from, to)
                .setStatusCode(HttpStatus.MOVED_PERMANENTLY);
    }

}
