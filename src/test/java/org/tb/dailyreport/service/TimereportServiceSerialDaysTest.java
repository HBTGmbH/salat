package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.tb.common.GlobalConstants.MAX_SERIAL_BOOKING_DAYS;
import static org.tb.common.exception.ErrorCode.TR_SERIAL_DAYS_OUT_OF_RANGE;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.common.exception.InvalidDataException;
import org.tb.dailyreport.persistence.PublicholidayDAO;

/**
 * The serial booking offers every value from 2 to {@value org.tb.common.GlobalConstants#MAX_SERIAL_BOOKING_DAYS}
 * working days (#826). Weekends and public holidays are skipped for every one of them, and a count
 * outside the offered range is rejected regardless of what the form sent.
 */
@ExtendWith(MockitoExtension.class)
class TimereportServiceSerialDaysTest {

  @InjectMocks
  private TimereportService classUnderTest;
  @Mock
  private PublicholidayDAO publicholidayDAO;

  /** Monday */
  private static final LocalDate START = LocalDate.of(2024, 1, 8);

  @BeforeEach
  void noPublicHolidays() {
    lenient().when(publicholidayDAO.getPublicHoliday(any())).thenReturn(Optional.empty());
  }

  @Test
  void every_count_between_one_and_the_maximum_yields_that_many_dates() {
    for (int count = 1; count <= MAX_SERIAL_BOOKING_DAYS; count++) {
      assertThat(classUnderTest.getWorkableSerialDates(START, count))
          .as("count %d", count)
          .hasSize(count);
    }
  }

  @Test
  void weekends_are_skipped() {
    // Monday plus six working days lands on the Tuesday of the following week
    assertThat(classUnderTest.getWorkableSerialDates(START, 7))
        .containsExactly(
            LocalDate.of(2024, 1, 8), LocalDate.of(2024, 1, 9), LocalDate.of(2024, 1, 10),
            LocalDate.of(2024, 1, 11), LocalDate.of(2024, 1, 12),
            LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 16));
  }

  @Test
  void a_public_holiday_is_skipped() {
    LocalDate holiday = LocalDate.of(2024, 1, 10);
    lenient().when(publicholidayDAO.getPublicHoliday(holiday))
        .thenReturn(Optional.of(new org.tb.dailyreport.domain.Publicholiday()));

    assertThat(classUnderTest.getWorkableSerialDates(START, 3))
        .containsExactly(LocalDate.of(2024, 1, 8), LocalDate.of(2024, 1, 9), LocalDate.of(2024, 1, 11));
  }

  @Test
  void a_count_beyond_the_maximum_is_rejected() {
    assertThatThrownBy(() -> classUnderTest.getWorkableSerialDates(START, MAX_SERIAL_BOOKING_DAYS + 1))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(TR_SERIAL_DAYS_OUT_OF_RANGE.getCode());
  }

  @Test
  void a_count_below_one_is_rejected() {
    assertThatThrownBy(() -> classUnderTest.getWorkableSerialDates(START, 0))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(TR_SERIAL_DAYS_OUT_OF_RANGE.getCode());
  }

}
