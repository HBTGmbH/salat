package org.tb.common.configuration;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

@Configuration
public class InternationalizationConfiguration {

  @Bean
  public MessageSourceAccessor messageSourceAccessor(MessageSource source) {
    return new MessageSourceAccessor(source);
  }

  @Bean
  public CookieLocaleResolver localeResolver(@Value("${salat.locale-cookie-name}") String cookieName) {
    var resolver = new CookieLocaleResolver(cookieName);
    // Allows the "auto" sentinel value written for browser-detection preference
    // to be silently ignored (falls back to Accept-Language) instead of throwing.
    resolver.setRejectInvalidCookies(false);
    resolver.setDefaultLocale(Locale.GERMAN);
    return resolver;
  }

}
