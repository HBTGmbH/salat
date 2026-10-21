package de.hbt.salat.notification.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hbt.salat.common.notification.NotificationKind;
import de.hbt.salat.common.notification.NotificationKindContributor;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/** The notification kinds the modules contribute (#828), in the order of the settings page. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class NotificationKindRegistryTest {

  @Test
  void lists_the_kinds_by_their_position() {
    NotificationKindContributor one = () -> List.of(NotificationKind.of("late", 30, false));
    NotificationKindContributor other = () -> List.of(NotificationKind.of("early", 10, true),
        NotificationKind.mailedAlways("middle", 20));

    assertThat(new NotificationKindRegistry(List.of(one, other)).all())
        .extracting(NotificationKind::getKey).containsExactly("early", "middle", "late");
  }

  @Test
  void a_key_contributed_twice_stops_the_start() {
    NotificationKindContributor one = () -> List.of(NotificationKind.of("same", 10, false));
    NotificationKindContributor other = () -> List.of(NotificationKind.of("same", 20, true));

    assertThatThrownBy(() -> new NotificationKindRegistry(List.of(one, other)))
        .isInstanceOf(IllegalStateException.class);
  }

  /**
   * The settings page builds the message key of each row at runtime, which the template check in
   * {@code MessageResourcesConsistencyTest} cannot see, so every contributor on the class path is
   * built without Spring and its kinds are checked here — the way {@code BetaFeaturesTest} does it.
   */
  @Test
  void every_contributed_kind_has_its_row_text_in_both_bundles_and_the_keys_are_unique() throws Exception {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(NotificationKindContributor.class));
    var contributors = new ArrayList<NotificationKindContributor>();
    for (var candidate : scanner.findCandidateComponents("de.hbt.salat")) {
      contributors.add((NotificationKindContributor) Class.forName(candidate.getBeanClassName())
          .getDeclaredConstructor().newInstance());
    }
    var kinds = new NotificationKindRegistry(contributors).all();
    assertThat(kinds).isNotEmpty();
    for (String bundle : List.of("/de/hbt/salat/web/MessageResources.properties",
        "/de/hbt/salat/web/MessageResources_en.properties")) {
      var properties = new Properties();
      try (Reader reader = new InputStreamReader(getClass().getResourceAsStream(bundle), UTF_8)) {
        properties.load(reader);
      }
      for (NotificationKind kind : kinds) {
        assertThat(properties).as(bundle).containsKey(kind.labelKey());
      }
    }
  }
}
