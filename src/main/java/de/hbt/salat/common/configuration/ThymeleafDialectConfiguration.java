package de.hbt.salat.common.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import de.hbt.salat.common.thymeleaf.SalatDialect;

@Configuration
public class ThymeleafDialectConfiguration {

    @Bean
    public SalatDialect salatDialect() {
        return new SalatDialect();
    }
}
