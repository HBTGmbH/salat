package org.tb.common.configuration;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.MessageSourceAccessor;

@Configuration
public class InternationalizationConfiguration {

  @Bean
  public MessageSourceAccessor messageSourceAccessor(MessageSource source) {
    return new MessageSourceAccessor(source);
  }

}
