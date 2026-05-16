package org.tb.common.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tb.common.thymeleaf.*;
import org.thymeleaf.dialect.IDialect;

@Configuration
public class ThymeleafConfiguration {

    @Bean
    public IDialect fragmentFactoriesDialect(
            DangerZoneParamsFactory dangerZoneParamsFactory,
            FilterCardParamsFactory filterCardParamsFactory,
            MasterTableParamsFactory masterTableParamsFactory,
            FormFieldParamsFactory formFieldParamsFactory
    ) {
        return new FragmentFactoriesDialect(
                dangerZoneParamsFactory,
                filterCardParamsFactory,
                masterTableParamsFactory,
                formFieldParamsFactory
        );
    }
}
