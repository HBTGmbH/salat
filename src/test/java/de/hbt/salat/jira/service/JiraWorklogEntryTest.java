package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The comment of a worklog (#1408): per person the sign and the share, sorted by sign, in the
 * notation JIRA uses for logged time.
 */
class JiraWorklogEntryTest {

  private static final LocalDate DAY = LocalDate.of(2026, 10, 7);

  @Test
  void names_each_sign_with_its_share_sorted_by_sign() {
    var entry = JiraWorklogEntry.of(DAY, Map.of("xyz", 150L, "abc", 240L));

    assertThat(entry.workDate()).isEqualTo(DAY);
    assertThat(entry.minutes()).isEqualTo(390);
    assertThat(entry.comment()).isEqualTo("Von HBT protokollierte Stunden übertragen:\nabc 4h\nxyz 2h 30m");
  }

  @Test
  void the_shares_in_the_comment_add_up_to_the_time_of_the_worklog() {
    var entry = JiraWorklogEntry.of(DAY, Map.of("abc", 61L, "def", 1L, "xyz", 59L));

    assertThat(entry.minutes()).isEqualTo(61 + 1 + 59);
    assertThat(entry.comment()).isEqualTo("Von HBT protokollierte Stunden übertragen:\nabc 1h 1m\ndef 1m\nxyz 59m");
  }

  @Test
  void leaves_out_a_share_of_zero_minutes() {
    var entry = JiraWorklogEntry.of(DAY, Map.of("abc", 0L, "xyz", 45L));

    assertThat(entry.minutes()).isEqualTo(45);
    assertThat(entry.comment()).isEqualTo("Von HBT protokollierte Stunden übertragen:\nxyz 45m");
  }

  @Test
  void a_worklog_of_one_person_names_that_person() {
    assertThat(JiraWorklogEntry.of(DAY, Map.of("abc", 480L)).comment())
        .isEqualTo("Von HBT protokollierte Stunden übertragen:\nabc 8h");
  }

  @ParameterizedTest
  @CsvSource({
      "240, 4h",
      "150, 2h 30m",
      "45,  45m",
      "1,   1m",
      "61,  1h 1m",
      "600, 10h",
      "1500, 25h"
  })
  void writes_a_share_in_hours_and_minutes(long minutes, String expected) {
    assertThat(JiraWorklogEntry.duration(minutes)).isEqualTo(expected);
  }
}
