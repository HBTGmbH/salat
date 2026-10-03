package de.hbt.salat.dailyreport.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Locale;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import de.hbt.salat.dailyreport.domain.ReportPeriod;

/**
 * Das Schloss an der Überschrift von Tag und Monat (#1164): nur das Icon, die Bedeutung im {@code title} und für
 * Screenreader. Im Monat ein Schloss für beides, dessen Titel nennt, wie weit Abnahme und Freigabe reichen.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReportPeriodFragmentTest {

  private static final YearMonth MAY = YearMonth.of(2026, 5);

  @Test
  void a_released_day_shows_the_lock_with_its_meaning_as_title() {
    var html = renderDay("committed");

    assertThat(html).contains("ti-lock").contains("title=\"Freigegeben\"");
    assertThat(html).containsPattern("class=\"visually-hidden\"\\s*>Freigegeben<");
    assertThat(html).contains("aria-hidden=\"true\"");
  }

  @Test
  void an_accepted_day_is_titled_accepted() {
    assertThat(renderDay("closed")).contains("title=\"Abgenommen\"");
  }

  @Test
  void an_open_day_shows_no_lock() {
    assertThat(renderDay("open")).doesNotContain("ti-lock");
    assertThat(renderDay(null)).doesNotContain("ti-lock");
  }

  @Test
  void a_month_accepted_in_part_and_released_in_part_has_one_lock_naming_both() {
    var html = renderMonth(new ReportPeriod.Month(MAY, MAY.atDay(8), MAY.atDay(15)));

    assertThat(html.split("ti-lock", -1)).hasSize(2);
    assertThat(html).contains("title=\"Abgenommen bis 08.05. · Freigegeben bis 15.05.\"");
  }

  @Test
  void a_month_released_as_a_whole_is_titled_released() {
    var html = renderMonth(new ReportPeriod.Month(MAY, null, MAY.atEndOfMonth()));

    assertThat(html).contains("title=\"Freigegeben\"");
  }

  @Test
  void a_month_accepted_as_a_whole_is_titled_accepted() {
    var html = renderMonth(new ReportPeriod.Month(MAY, MAY.atEndOfMonth(), null));

    assertThat(html).contains("title=\"Abgenommen\"");
  }

  @Test
  void an_open_month_shows_no_lock() {
    assertThat(renderMonth(new ReportPeriod.Month(MAY, null, null))).doesNotContain("ti-lock");
    assertThat(renderMonth(null)).doesNotContain("ti-lock");
  }

  private static String renderDay(String status) {
    var variables = new HashMap<String, Object>();
    variables.put("status", status);
    return render("<span th:replace=\"~{fragments/report-period :: dayStatus(${status})}\"></span>", variables);
  }

  private static String renderMonth(ReportPeriod.Month period) {
    var variables = new HashMap<String, Object>();
    variables.put("period", period);
    return render("<span th:replace=\"~{fragments/report-period :: monthStatus(${period})}\"></span>", variables);
  }

  private static String render(String caller, HashMap<String, Object> variables) {
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

    return engine.process(caller, new Context(Locale.GERMANY, variables));
  }
}
