package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.PublicholidayRepository;

/**
 * "letzter Arbeitstag" of the command palette (#1158): the day before, as long as it is a weekday
 * and no public holiday — across a weekend, a month and a year, and across holidays next to a
 * weekend. The holidays are the ones the service generates: Christmas Eve and New Year's Eve count.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class PublicholidayServiceLastWorkdayTest {

  @Mock
  private PublicholidayRepository publicholidayRepository;
  @Mock
  private PublicholidayDAO publicholidayDAO;

  @InjectMocks
  private PublicholidayService service;

  @Test
  void is_yesterday_on_a_tuesday() {
    noHolidays();

    assertThat(service.getLastWorkdayBefore(LocalDate.of(2026, 9, 29))).isEqualTo(LocalDate.of(2026, 9, 28));
  }

  @Test
  void is_the_friday_before_on_a_monday() {
    noHolidays();

    assertThat(service.getLastWorkdayBefore(LocalDate.of(2026, 9, 28))).isEqualTo(LocalDate.of(2026, 9, 25));
  }

  @Test
  void is_the_friday_before_on_a_sunday_as_well() {
    noHolidays();

    assertThat(service.getLastWorkdayBefore(LocalDate.of(2026, 9, 27))).isEqualTo(LocalDate.of(2026, 9, 25));
  }

  @Test
  void reaches_into_the_month_before() {
    noHolidays();

    assertThat(service.getLastWorkdayBefore(LocalDate.of(2026, 6, 1))).isEqualTo(LocalDate.of(2026, 5, 29));
  }

  /** Easter: Good Friday and Easter Monday frame the weekend, so the Tuesday looks back to Thursday. */
  @Test
  void skips_holidays_next_to_a_weekend() {
    holidays(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 5), LocalDate.of(2026, 4, 6));

    assertThat(service.getLastWorkdayBefore(LocalDate.of(2026, 4, 7))).isEqualTo(LocalDate.of(2026, 4, 2));
  }

  /** New Year's Day and New Year's Eve are both holidays: on 2 January 2026 it is the 30th. */
  @Test
  void reaches_into_the_year_before_past_new_year() {
    holidays(LocalDate.of(2025, 12, 24), LocalDate.of(2025, 12, 25), LocalDate.of(2025, 12, 26),
        LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1));

    assertThat(service.getLastWorkdayBefore(LocalDate.of(2026, 1, 2))).isEqualTo(LocalDate.of(2025, 12, 30));
  }

  private void noHolidays() {
    when(publicholidayDAO.getPublicHolidaysBetween(any(), any())).thenReturn(List.of());
  }

  private void holidays(LocalDate... days) {
    when(publicholidayDAO.getPublicHolidaysBetween(any(), any()))
        .thenReturn(Stream.of(days).map(day -> new Publicholiday(day, "Feiertag")).toList());
  }
}
