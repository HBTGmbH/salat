package org.tb.common.configuration;

import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

@Configuration
public class InternationalizationConfiguration {

  // Sentinel stored in the cookie when the user chose "browser detection".
  // CookieLocaleResolver rejectInvalidCookies=false silently ignores this
  // unparseable value and falls back to Accept-Language — exactly what we want.
  public static final String LOCALE_COOKIE_VALUE_AUTO = "auto";

  @Bean
  public MessageSourceAccessor messageSourceAccessor(MessageSource source) {
    return new MessageSourceAccessor(source);
  }

  @Bean
  public CookieLocaleResolver localeResolver(@Value("${salat.locale-cookie-name}") String cookieName) {
    var resolver = new CookieLocaleResolver(cookieName) {
      @Override
      protected @Nullable Locale parseLocaleValue(String localeValue) {
        if(LOCALE_COOKIE_VALUE_AUTO.equals(localeValue)) return null;
        return super.parseLocaleValue(localeValue);
      }
    };
    // Allows the "auto" sentinel value written for browser-detection preference
    // to be silently ignored (falls back to Accept-Language) instead of throwing.
    resolver.setRejectInvalidCookies(false);
    return resolver;
  }

}
