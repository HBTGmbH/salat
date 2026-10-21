package de.hbt.salat.employee.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import de.hbt.salat.common.notification.NotificationKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The channels of each notification kind (#828), stored as deviations from the defaults: a mail
 * Salat sent before stays on, a new one starts off, a reminder is always mailed.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class NotificationChannelPreferencesTest {

  private static final NotificationKind MAILED_BY_DEFAULT = NotificationKind.of("mailed", 10, true);
  private static final NotificationKind NOT_MAILED_BY_DEFAULT = NotificationKind.of("quiet", 20, false);
  private static final NotificationKind REMINDER = NotificationKind.mailedAlways("reminder", 30);
  private static final List<NotificationKind> KINDS = List.of(MAILED_BY_DEFAULT, NOT_MAILED_BY_DEFAULT, REMINDER);

  @Test
  void without_a_choice_every_kind_goes_to_the_bell_and_is_mailed_by_its_default() {
    var preferences = NotificationChannelPreferences.from(Map.of());

    assertThat(KINDS).allMatch(preferences::bell);
    assertThat(preferences.mail(MAILED_BY_DEFAULT)).isTrue();
    assertThat(preferences.mail(NOT_MAILED_BY_DEFAULT)).isFalse();
    assertThat(preferences.mail(REMINDER)).isTrue();
  }

  @Test
  void the_defaults_leave_nothing_to_store() {
    var preferences = NotificationChannelPreferences.of(KINDS, List.of("mailed", "quiet", "reminder"), List.of("mailed"));

    assertThat(preferences.toMap()).isEmpty();
  }

  @Test
  void stores_only_what_deviates_and_reads_it_back() {
    var preferences = NotificationChannelPreferences.of(KINDS, List.of("mailed"), List.of("quiet"));

    var stored = preferences.toMap();
    assertThat(stored).isEqualTo(Map.of(
        "bellOff", List.of("quiet", "reminder"),
        "mailOn", List.of("quiet"),
        "mailOff", List.of("mailed")));

    var read = NotificationChannelPreferences.from(stored);
    assertThat(read.bell(MAILED_BY_DEFAULT)).isTrue();
    assertThat(read.bell(NOT_MAILED_BY_DEFAULT)).isFalse();
    assertThat(read.mail(MAILED_BY_DEFAULT)).isFalse();
    assertThat(read.mail(NOT_MAILED_BY_DEFAULT)).isTrue();
  }

  /** The disabled switch of a reminder is not sent with the form; its mail stays on all the same. */
  @Test
  void a_reminder_is_mailed_whatever_is_sent_for_it() {
    var preferences = NotificationChannelPreferences.of(KINDS, List.of(), null);

    assertThat(preferences.mail(REMINDER)).isTrue();
    assertThat(preferences.bell(REMINDER)).isFalse();
  }

  @Test
  void a_missing_or_malformed_section_counts_as_no_choice() {
    assertThat(NotificationChannelPreferences.from(null)).isEqualTo(NotificationChannelPreferences.defaults());
    assertThat(NotificationChannelPreferences.from(Map.of("bellOff", "quiet", "mailOn", List.of(1))))
        .isEqualTo(NotificationChannelPreferences.defaults());
  }

  @Test
  void a_choice_for_a_kind_that_is_gone_is_dropped_with_the_next_save() {
    var stored = Map.<String, Object>of("bellOff", List.of("gone"));

    assertThat(NotificationChannelPreferences.from(stored).bell(MAILED_BY_DEFAULT)).isTrue();
    assertThat(NotificationChannelPreferences.of(KINDS, List.of("mailed", "quiet", "reminder"), List.of("mailed")).toMap())
        .isEmpty();
  }
}
