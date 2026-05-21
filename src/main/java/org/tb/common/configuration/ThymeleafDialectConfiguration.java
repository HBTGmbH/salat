package org.tb.common.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tb.common.thymeleaf.SalatDialect;

@Configuration
public class ThymeleafDialectConfiguration {

    @Bean
    public SalatDialect salatDialect() {
        return new SalatDialect();
    }
}
