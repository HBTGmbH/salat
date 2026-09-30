package de.hbt.salat.common.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Das Badge „Inaktiv" hinter dem Namen eines inaktiven Listeneintrags (#1220): grau, mit dem vorhandenen Text
 * {@code main.general.inactive.text}, und bei einem aktiven Eintrag gar nicht da.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class InactiveBadgeFragmentTest {

  @Test
  void an_inactive_entry_gets_the_grey_badge_with_the_german_text() {
    var html = render(true, Locale.GERMANY);

    assertThat(html).containsPattern("<span class=\"badge bg-secondary-lt ms-1\"\\s*>Inaktiv</span>");
  }

  @Test
  void the_english_bundle_names_it_inactive() {
    assertThat(render(true, Locale.ENGLISH)).contains(">Inactive</span>");
  }

  @Test
  void an_active_entry_shows_nothing() {
    assertThat(render(false, Locale.GERMANY)).doesNotContain("badge").doesNotContain("Inaktiv");
  }

  private static String render(boolean inactive, Locale locale) {
    var fragments = new ClassLoaderTemplateResolver();
    fragments.setPrefix("templates/");
    fragments.setSuffix(".html");
    fragments.setCharacterEncoding("UTF-8");
    fragments.setCheckExistence(true);
    fragments.setOrder(1);
    var callerResolver = new StringTemplateResolver();
    callerResolver.setOrder(2);

    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);

    var engine = new SpringTemplateEngine();
    engine.addTemplateResolver(fragments);
    engine.addTemplateResolver(callerResolver);
    engine.setTemplateEngineMessageSource(messageSource);

    return engine.process("<td>Name<span th:replace=\"~{fragments/inactive-badge :: badge(${inactive})}\"></span></td>",
        new Context(locale, Map.of("inactive", inactive)));
  }
}
