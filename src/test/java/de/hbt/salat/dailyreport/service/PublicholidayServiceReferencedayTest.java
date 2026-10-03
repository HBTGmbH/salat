package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.auth.event.AuthorizedUserChangedEvent;
import de.hbt.salat.dailyreport.domain.Referenceday;
import de.hbt.salat.dailyreport.persistence.PublicholidayDAO;
import de.hbt.salat.dailyreport.persistence.PublicholidayRepository;
import de.hbt.salat.dailyreport.persistence.ReferencedayRepository;

/**
 * A reference day that exists before the holidays of its year — somebody booked ahead — takes the holiday over once
 * the holidays are generated (#1211). Until then it stayed a working day for good.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class PublicholidayServiceReferencedayTest {

  private static final LocalDate CHRISTMAS = LocalDate.of(2026, 12, 25);

  @Mock
  private PublicholidayRepository publicholidayRepository;
  @Mock
  private PublicholidayDAO publicholidayDAO;
  @Mock
  private ReferencedayRepository referencedayRepository;

  @InjectMocks
  private PublicholidayService service;

  @Test
  void a_reference_day_booked_ahead_takes_the_generated_holiday_over() {
    var bookedAhead = new Referenceday();
    bookedAhead.setRefdate(CHRISTMAS);
    bookedAhead.applyCalendar(null);
    when(publicholidayRepository.findAll()).thenReturn(List.of());
    when(referencedayRepository.findAllByRefdateIn(any())).thenAnswer(invocation ->
        invocation.<Collection<LocalDate>>getArgument(0).contains(CHRISTMAS) ? List.of(bookedAhead) : List.of());

    service.checkPublicHolidaysForCurrentYear(new AuthorizedUserChangedEvent(this));

    assertThat(bookedAhead.getHoliday()).isTrue();
    assertThat(bookedAhead.getName()).isEqualTo("1. Weihnachtstag");
    assertThat(bookedAhead.getWorkingday()).isFalse();
  }
}
