package de.hbt.salat.employee.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** How the years with the company are counted (#1501). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TenureTest {

  private static final long EMPLOYEE_ID = 7L;

  @Test
  void a_contract_without_an_end_reaches_every_anniversary() {
    var tenure = Tenure.of(List.of(span("2021-03-01", null)), null);

    assertThat(tenure.reachedOn(1)).contains(date("2022-03-01"));
    assertThat(tenure.reachedOn(5)).contains(date("2026-03-01"));
    assertThat(tenure.reachedOn(25)).contains(date("2046-03-01"));
  }

  @Test
  void a_gap_between_two_contracts_moves_the_anniversary_on_by_its_length() {
    // three years, a year off, then on: five years are complete after two more
    var tenure = Tenure.of(List.of(span("2010-01-01", "2012-12-31"), span("2014-01-01", null)), null);

    assertThat(tenure.reachedOn(1)).contains(date("2011-01-01"));
    assertThat(tenure.reachedOn(5)).contains(date("2016-01-01"));
  }

  @Test
  void every_gap_before_the_anniversary_counts() {
    // ten days off twice
    var tenure = Tenure.of(List.of(span("2020-01-01", "2020-06-30"), span("2020-07-11", "2020-12-31"),
        span("2021-01-11", null)), null);

    assertThat(tenure.reachedOn(1)).contains(date("2021-01-21"));
  }

  @Test
  void a_gap_after_the_anniversary_does_not_count() {
    var tenure = Tenure.of(List.of(span("2020-01-01", "2021-06-30"), span("2022-01-01", null)), null);

    assertThat(tenure.reachedOn(1)).contains(date("2021-01-01"));
  }

  @Test
  void the_entry_before_salat_moves_the_start_back() {
    var tenure = Tenure.of(List.of(span("2007-01-01", null)), date("1998-04-01"));

    assertThat(tenure.reachedOn(25)).contains(date("2023-04-01"));
  }

  @Test
  void an_entry_date_after_the_first_contract_changes_nothing() {
    var tenure = Tenure.of(List.of(span("2007-01-01", null)), date("2008-01-01"));

    assertThat(tenure.reachedOn(1)).contains(date("2008-01-01"));
  }

  @Test
  void an_anniversary_after_the_last_contract_ends_is_not_reached() {
    var tenure = Tenure.of(List.of(span("2024-01-01", "2026-12-31")), null);

    assertThat(tenure.reachedOn(1)).contains(date("2025-01-01"));
    assertThat(tenure.reachedOn(5)).isEmpty();
  }

  @Test
  void overlapping_and_adjoining_contracts_count_once() {
    var tenure = Tenure.of(List.of(span("2021-01-01", "2021-12-31"), span("2021-06-01", "2022-06-30"),
        span("2022-07-01", null)), null);

    assertThat(tenure.reachedOn(5)).contains(date("2026-01-01"));
  }

  @Test
  void the_order_of_the_contracts_does_not_matter() {
    var tenure = Tenure.of(List.of(span("2014-01-01", null), span("2010-01-01", "2012-12-31")), null);

    assertThat(tenure.reachedOn(5)).contains(date("2016-01-01"));
  }

  @Test
  void the_hundredth_day_counts_the_first_day_as_day_one_and_skips_gaps() {
    assertThat(Tenure.of(List.of(span("2026-09-01", null)), null).reachedAfterDays(100)).contains(date("2026-12-09"));
    // ten days off in October push the hundredth day on by ten
    var tenure = Tenure.of(List.of(span("2026-09-01", "2026-09-30"), span("2026-10-11", null)), null);
    assertThat(tenure.reachedAfterDays(100)).contains(date("2026-12-19"));
  }

  @Test
  void without_a_contract_nothing_is_reached() {
    assertThat(Tenure.of(List.of(), date("2000-01-01")).reachedOn(1)).isEmpty();
  }

  private static EmploymentSpan span(String from, String until) {
    return new EmploymentSpan(EMPLOYEE_ID, date(from), until == null ? null : date(until));
  }

  private static LocalDate date(String text) {
    return LocalDate.parse(text);
  }
}
