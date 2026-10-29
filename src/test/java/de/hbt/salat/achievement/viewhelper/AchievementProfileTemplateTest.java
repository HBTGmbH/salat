package de.hbt.salat.achievement.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.hbt.salat.achievement.domain.AchievementProfile;
import de.hbt.salat.achievement.domain.AchievementProfile.Reached;
import de.hbt.salat.achievement.service.AchievementService;
import de.hbt.salat.common.achievement.AchievementDefinition;
import de.hbt.salat.dailyreport.service.TrainingYearAchievementContributor;
import de.hbt.salat.employee.service.TenureAchievementContributor;
import de.hbt.salat.palette.service.PaletteAchievementContributor;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.StaticApplicationContext;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** Renders both complete profile views, including their shared medals and replay dialogs (#1553). */
class AchievementProfileTemplateTest {

  private static final AchievementDefinition TENURE = TenureAchievementContributor.TENURE;
  private static final AchievementDefinition YEARLY = TrainingYearAchievementContributor.TRAINING_YEAR;
  private static final AchievementDefinition PALETTE = PaletteAchievementContributor.PALETTE;
  private static final List<Reached> REACHED = List.of(
      reached(PALETTE, 100, "2026-06-22"), reached(TENURE, 10, "2026-04-15"),
      reached(YEARLY, 2, "2026-03-01"), reached(YEARLY, 1, "2025-03-01"), reached(TENURE, 5, "2021-04-15"));
  private static final List<AchievementProfile.Next> NEXT = List.of(
      new AchievementProfile.Next(TENURE, 15, LocalDate.of(2031, 4, 15), null));
  private static final Pattern TARGET = Pattern.compile("data-bs-target=\"#(achievementReached-[^\"]+)\"");

  @Test
  void the_date_view_keeps_the_chronological_grid_without_type_headings() {
    var html = render("date", REACHED, Locale.GERMAN);

    assertThat(targets(html)).containsExactlyElementsOf(REACHED.stream().map(AchievementProfileTemplateTest::id).toList());
    assertThat(html).doesNotContain("id=\"achievementGroup-")
        .containsPattern("href=\"/profile\\?fAchievementSort=date\"[^>]*aria-current=\"true\"")
        .contains("Zeitpunkt", "Typ, dann Zeitpunkt", "Als Nächstes");
  }

  @Test
  void the_type_view_has_only_reached_groups_in_definition_order() {
    var html = render("type", REACHED, Locale.GERMAN);

    assertThat(Pattern.compile("<h4[^>]*id=\"achievementGroup-[^\"]+\"[^>]*>([^<]+)</h4>")
        .matcher(html).results().map(match -> match.group(1)).toList())
        .containsExactly("Loyalist", "Fortbildungsjahr", "Palette Junkie");
    assertThat(targets(html)).containsExactly(id(REACHED.get(1)), id(REACHED.get(4)),
        id(REACHED.get(2)), id(REACHED.get(3)), id(REACHED.get(0)));
    assertThat(html).containsPattern("href=\"/profile\\?fAchievementSort=type\"[^>]*aria-current=\"true\"");
    assertThat(html.indexOf("Als Nächstes")).isGreaterThan(html.indexOf("id=\"achievementGroup-palette\""));
  }

  @ParameterizedTest
  @ValueSource(strings = {"date", "type"})
  void each_medal_targets_its_own_dialog_and_all_element_ids_are_unique(String sort) {
    var html = render(sort, REACHED, Locale.GERMAN);

    assertThat(targets(html)).hasSize(REACHED.size()).doesNotHaveDuplicates();
    for (var target : targets(html)) {
      assertThat(html).contains("id=\"" + target + "\"");
    }
    assertThat(Pattern.compile("\\bid=\"([^\"]+)\"").matcher(html).results().map(match -> match.group(1)).toList())
        .doesNotHaveDuplicates();
    assertThat(html.split("data-achievement-dialog=\"replay\"", -1)).hasSize(REACHED.size() + 1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"date", "type"})
  void without_reached_medals_there_are_no_groups_or_dialogs_but_next_is_still_shown(String sort) {
    var html = render(sort, List.of(), Locale.GERMAN);

    assertThat(targets(html)).isEmpty();
    assertThat(html).doesNotContain("id=\"achievementGroup-", "data-achievement-dialog=\"replay\"")
        .contains("Als Nächstes", "Säule");
  }

  @Test
  void the_sort_choice_and_type_headings_are_translated() {
    assertThat(render("type", REACHED, Locale.ENGLISH))
        .contains("Sort by:", "Date reached", "Type, then date reached", "Training year")
        .doesNotContain("Typ, dann Zeitpunkt", "Fortbildungsjahr");
  }

  private static String render(String sort, List<Reached> reached, Locale locale) {
    var messages = new ResourceBundleMessageSource();
    messages.setBasename("de/hbt/salat/web/MessageResources");
    messages.setDefaultEncoding("UTF-8");
    messages.setFallbackToSystemLocale(false);
    var resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setCharacterEncoding("UTF-8");
    var engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.setTemplateEngineMessageSource(messages);
    engine.setLinkBuilder(new StandardLinkBuilder() {
      @Override
      protected String computeContextPath(IExpressionContext context, String base, Map<String, Object> parameters) {
        return "";
      }
    });
    try (var beans = new StaticApplicationContext()) {
      beans.getBeanFactory().registerSingleton("achievementViewHelper", new AchievementViewHelper(
          mock(AchievementService.class), new MessageSourceAccessor(messages, locale), new AchievementQuotes()));
      beans.refresh();
      var context = new Context(locale, Map.of("profile", new AchievementProfile(reached, NEXT),
          "achievementSort", sort, "today", LocalDate.of(2026, 6, 25)));
      context.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
          new ThymeleafEvaluationContext(beans, null));
      return engine.process("achievement/profile", Set.of("main"), context);
    }
  }

  private static List<String> targets(String html) {
    return TARGET.matcher(html).results().map(match -> match.group(1)).toList();
  }

  private static String id(Reached reached) {
    return "achievementReached-" + reached.definition().getKey() + "-" + reached.level() + "-" + reached.achievedOn();
  }

  private static Reached reached(AchievementDefinition definition, int level, String day) {
    return new Reached(definition, level, definition.tierOf(level), LocalDate.parse(day));
  }
}
