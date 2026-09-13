package org.tb.budget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The segment listing evaluates every budgeted order, so it is never shown without a window (#779).
 * What the request leaves open the running quarter fills in.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class SegmentControllingFilterFormTest {

  @Test
  public void an_empty_form_falls_back_to_the_running_quarter() {
    var form = new SegmentControllingFilterForm();

    form.applyDefaults(LocalDate.of(2026, 5, 17));

    assertThat(form.getFrom()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(form.getUntil()).isEqualTo(LocalDate.of(2026, 6, 30));
  }

  @Test
  public void the_first_and_the_last_quarter_of_a_year_are_no_special_case() {
    var first = new SegmentControllingFilterForm();
    first.applyDefaults(LocalDate.of(2026, 1, 1));
    assertThat(first.getFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(first.getUntil()).isEqualTo(LocalDate.of(2026, 3, 31));

    var last = new SegmentControllingFilterForm();
    last.applyDefaults(LocalDate.of(2026, 12, 31));
    assertThat(last.getFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(last.getUntil()).isEqualTo(LocalDate.of(2026, 12, 31));
  }

  @Test
  public void a_chosen_window_is_left_alone() {
    var form = new SegmentControllingFilterForm();
    form.setFrom(LocalDate.of(2025, 2, 3));
    form.setUntil(LocalDate.of(2025, 11, 4));

    form.applyDefaults(LocalDate.of(2026, 5, 17));

    assertThat(form.getFrom()).isEqualTo(LocalDate.of(2025, 2, 3));
    assertThat(form.getUntil()).isEqualTo(LocalDate.of(2025, 11, 4));
  }

  /** Narrowing one end must not drag the other one to today's quarter. */
  @Test
  public void an_open_end_is_closed_at_the_end_of_the_quarter_the_start_lies_in() {
    var form = new SegmentControllingFilterForm();
    form.setFrom(LocalDate.of(2025, 2, 3));

    form.applyDefaults(LocalDate.of(2026, 5, 17));

    assertThat(form.getUntil()).isEqualTo(LocalDate.of(2025, 3, 31));
  }

  @Test
  public void an_open_start_opens_the_quarter_of_today() {
    var form = new SegmentControllingFilterForm();
    form.setUntil(LocalDate.of(2026, 5, 20));

    form.applyDefaults(LocalDate.of(2026, 5, 17));

    assertThat(form.getFrom()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(form.getUntil()).isEqualTo(LocalDate.of(2026, 5, 20));
  }
}
