package de.hbt.salat.achievement;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import de.hbt.salat.common.achievement.AchievementContributor;
import de.hbt.salat.common.achievement.AchievementDefinition;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * The profile page, the popup and the notification build the message keys of an achievement at
 * runtime, which the template check in {@code MessageResourcesConsistencyTest} cannot see (#1501).
 * The achievements come from the modules, so the check finds every {@link AchievementContributor} on
 * the class path and reads its constants — the contributors need Spring to be built.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class AchievementTextsTest {

  @Test
  void every_contributed_achievement_has_its_texts_in_both_bundles_and_names_every_level() throws Exception {
    var definitions = contributedAchievements();
    assertThat(definitions).isNotEmpty();
    for (var bundle : List.of("/de/hbt/salat/web/MessageResources.properties",
        "/de/hbt/salat/web/MessageResources_en.properties")) {
      var properties = new Properties();
      try (Reader reader = new InputStreamReader(getClass().getResourceAsStream(bundle), UTF_8)) {
        properties.load(reader);
      }
      for (var definition : definitions) {
        assertThat(properties).as(bundle)
            .containsKeys(definition.groupKey(), definition.titleKey(), definition.levelKey());
        for (int level : definition.getLevels()) {
          var title = new MessageFormat(properties.getProperty(definition.titleKey()), Locale.GERMAN)
              .format(new Object[] {level});
          assertThat(title).as(bundle + " " + definition + " " + level).contains(String.valueOf(level));
        }
      }
    }
  }

  private static List<AchievementDefinition> contributedAchievements() throws Exception {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(AchievementContributor.class));
    var definitions = new ArrayList<AchievementDefinition>();
    for (var candidate : scanner.findCandidateComponents("de.hbt.salat")) {
      for (var field : Class.forName(candidate.getBeanClassName()).getFields()) {
        if (Modifier.isStatic(field.getModifiers()) && field.getType() == AchievementDefinition.class) {
          definitions.add((AchievementDefinition) field.get(null));
        }
      }
    }
    return definitions;
  }
}
