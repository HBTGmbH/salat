package org.tb.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.common.test.FixedClock;
import org.tb.common.util.DateUtils;

/**
 * The rule from #950, case by case: inactive means the validity range lies entirely in the past.
 */
@FixedClock("2026-06-25T10:15:30")
@DisplayNameGeneration(ReplaceUnderscores.class)
class ValidityTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);

  @Test
  void an_end_before_today_is_inactive() {
    assertThat(Validity.isInactive(TODAY.minusDays(1))).isTrue();
  }

  @Test
  void an_end_on_today_is_still_active() {
    assertThat(Validity.isInactive(TODAY)).isFalse();
  }

  @Test
  void an_end_after_today_is_active() {
    assertThat(Validity.isInactive(TODAY.plusDays(1))).isFalse();
  }

  @Test
  void an_open_end_stored_as_null_is_never_inactive() {
    assertThat(Validity.isInactive(null)).isFalse();
  }

  @Test
  void an_open_end_stored_as_the_sentinel_is_never_inactive() {
    assertThat(Validity.isInactive(LocalDateRange.FINIT_UNTIL_BOUNDARY)).isFalse();
    assertThat(Validity.isInactive(LocalDate.of(2999, 12, 31))).isFalse();
  }

  /**
   * The start is deliberately not part of the question — a record entered ahead of time is not
   * inactive but merely not yet active, and it has to stay visible.
   */
  @Test
  void a_start_in_the_future_does_not_make_a_record_inactive() {
    assertThat(Validity.isInactive(TODAY.plusMonths(2))).isFalse();
  }

  @Test
  void it_compares_against_the_shared_today() {
    assertThat(DateUtils.today()).isEqualTo(TODAY);
    assertThat(Validity.isInactive(DateUtils.today())).isFalse();
    assertThat(Validity.isInactive(DateUtils.today().minusDays(1))).isTrue();
  }

  @Test
  void it_answers_the_same_question_for_any_other_day() {
    var reference = LocalDate.of(2030, 3, 15);

    assertThat(Validity.isInactiveOn(reference.minusDays(1), reference)).isTrue();
    assertThat(Validity.isInactiveOn(reference, reference)).isFalse();
    assertThat(Validity.isInactiveOn(reference.plusDays(1), reference)).isFalse();
    assertThat(Validity.isInactiveOn(null, reference)).isFalse();
  }
}
