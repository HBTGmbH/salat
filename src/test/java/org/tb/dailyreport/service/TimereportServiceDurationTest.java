package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tb.common.exception.ErrorCode.TR_DURATION_EXCEEDS_ONE_DAY;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tb.common.exception.InvalidDataException;
import org.tb.dailyreport.domain.Referenceday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.persistence.ReferencedayRepository;
import org.tb.dailyreport.persistence.TimereportRepository;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Employeeorder;
import org.tb.order.persistence.EmployeeorderDAO;

/**
 * A booking must never be longer than the day it belongs to. The limit used to live in the booking
 * form's controller only, so the inline edit of the daily view happily stored 55:30 (#825) - it is
 * now enforced in the service, which every caller has to pass through.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimereportServiceDurationTest {

  private static final long EMPLOYEE_CONTRACT_ID = 1L;
  private static final long EMPLOYEE_ORDER_ID = 2L;
  private static final long TIMEREPORT_ID = 3L;
  private static final LocalDate DATE = LocalDate.now();

  @InjectMocks
  private TimereportService classUnderTest;

  @Mock
  private EmployeecontractDAO employeecontractDAO;
  @Mock
  private EmployeeorderDAO employeeorderDAO;
  @Mock
  private ReferencedayRepository referencedayRepository;
  @Mock
  private TimereportRepository timereportRepository;

  private void givenValidMasterData() {
    when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID))
        .thenReturn(new Employeecontract());
    when(employeeorderDAO.getEmployeeorderById(EMPLOYEE_ORDER_ID)).thenReturn(new Employeeorder());
    var referenceday = new Referenceday();
    referenceday.setRefdate(DATE);
    when(referencedayRepository.findByRefdate(DATE)).thenReturn(Optional.of(referenceday));
    when(timereportRepository.findById(TIMEREPORT_ID)).thenReturn(Optional.of(new Timereport()));
  }

  @Test
  void creating_a_timereport_longer_than_a_day_is_rejected() {
    givenValidMasterData();

    assertThatThrownBy(() -> create(24, 1))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(TR_DURATION_EXCEEDS_ONE_DAY.getCode());

    verify(timereportRepository, never()).save(any());
  }

  @Test
  void updating_a_timereport_to_more_than_a_day_is_rejected() {
    givenValidMasterData();

    // the value from the bug report, entered as 5530 in the inline edit of the daily view
    assertThatThrownBy(() -> classUnderTest.updateTimereport(TIMEREPORT_ID, EMPLOYEE_CONTRACT_ID,
        EMPLOYEE_ORDER_ID, DATE, "comment", false, 55, 30))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(TR_DURATION_EXCEEDS_ONE_DAY.getCode());

    verify(timereportRepository, never()).save(any());
  }

  /**
   * The minutes part counts towards the limit even when the caller did not normalise it, so neither
   * 00:1500 nor 23:61 slips past the check.
   */
  @Test
  void an_excessive_minutes_part_counts_towards_the_limit() {
    givenValidMasterData();

    assertThatThrownBy(() -> create(0, 1500))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(TR_DURATION_EXCEEDS_ONE_DAY.getCode());
    assertThatThrownBy(() -> create(23, 61))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(TR_DURATION_EXCEEDS_ONE_DAY.getCode());

    verify(timereportRepository, never()).save(any());
  }

  /**
   * Exactly one day is a valid duration. With the collaborators mocked out the call may still fail
   * further down the line - but not on the duration check.
   */
  @Test
  void a_duration_of_exactly_one_day_passes_the_duration_check() {
    givenValidMasterData();

    Throwable thrown = catchThrowable(() -> create(24, 0));

    if (thrown != null) {
      assertThat(thrown).hasMessageNotContaining(TR_DURATION_EXCEEDS_ONE_DAY.getCode());
    }
  }

  private void create(long hours, long minutes) throws Exception {
    classUnderTest.createTimereports(EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, DATE, "comment",
        false, hours, minutes, 1);
  }

}
