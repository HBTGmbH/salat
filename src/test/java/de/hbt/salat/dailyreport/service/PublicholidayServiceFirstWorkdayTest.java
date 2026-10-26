package de.hbt.salat.dailyreport.service;

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
import de.hbt.salat.dailyreport.domain.Publicholiday;
import de.hbt.salat.dailyreport.persistence.PublicholidayDAO;

/**
 * "Nächster Arbeitstag" of the daily view (#1541): the day after, as long as it is a weekday and no
 * public holiday — across a weekend, a month and a year, and across holidays next to a weekend. The
 * holidays are the ones the service generates: Christmas Eve and New Year's Eve count.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class PublicholidayServiceFirstWorkdayTest {

  @Mock
  private PublicholidayDAO publicholidayDAO;

  @InjectMocks
  private PublicholidayService service;

  @Test
  void is_tomorrow_on_a_thursday() {
    noHolidays();

    assertThat(service.getFirstWorkdayAfter(LocalDate.of(2026, 10, 22))).isEqualTo(LocalDate.of(2026, 10, 23));
  }

  @Test
  void is_the_monday_after_on_a_friday() {
    noHolidays();

    assertThat(service.getFirstWorkdayAfter(LocalDate.of(2026, 10, 23))).isEqualTo(LocalDate.of(2026, 10, 26));
  }

  @Test
  void is_the_monday_after_on_a_saturday_as_well() {
    noHolidays();

    assertThat(service.getFirstWorkdayAfter(LocalDate.of(2026, 10, 24))).isEqualTo(LocalDate.of(2026, 10, 26));
  }

  @Test
  void reaches_into_the_month_after() {
    noHolidays();

    assertThat(service.getFirstWorkdayAfter(LocalDate.of(2026, 7, 31))).isEqualTo(LocalDate.of(2026, 8, 3));
  }

  /** Easter: Good Friday and Easter Monday frame the weekend, so the Thursday looks ahead to Tuesday. */
  @Test
  void skips_holidays_next_to_a_weekend() {
    holidays(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 5), LocalDate.of(2026, 4, 6));

    assertThat(service.getFirstWorkdayAfter(LocalDate.of(2026, 4, 2))).isEqualTo(LocalDate.of(2026, 4, 7));
  }

  /** New Year's Eve and New Year's Day are both holidays: on 30 December 2026 it is 4 January. */
  @Test
  void reaches_into_the_year_after_past_new_year() {
    holidays(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1));

    assertThat(service.getFirstWorkdayAfter(LocalDate.of(2026, 12, 30))).isEqualTo(LocalDate.of(2027, 1, 4));
  }

  private void noHolidays() {
    when(publicholidayDAO.getPublicHolidaysBetween(any(), any())).thenReturn(List.of());
  }

  private void holidays(LocalDate... days) {
    when(publicholidayDAO.getPublicHolidaysBetween(any(), any()))
        .thenReturn(Stream.of(days).map(day -> new Publicholiday(day, "Feiertag")).toList());
  }
}
