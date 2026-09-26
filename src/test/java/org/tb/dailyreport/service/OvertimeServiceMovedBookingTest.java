package org.tb.dailyreport.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.event.TimereportsCreatedOrUpdatedEvent;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.service.EmployeeorderService;

/**
 * {@code overtimeStatic} holds the overtime up to the acceptance date. A booking that leaves that
 * period is still in it and counts in the dynamic part at the same time — until the next
 * recalculation it counted twice (#1125). The recalculation therefore has to look at the day the
 * booking left as well, not only at the one it arrived on.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class OvertimeServiceMovedBookingTest {

  private static final long TIMEREPORT_ID = 3L;
  private static final long EMPLOYEE_CONTRACT_ID = 1L;
  private static final LocalDate ACCEPTANCE_DATE = LocalDate.parse("2026-02-28");
  private static final LocalDate ACCEPTED_DAY = LocalDate.parse("2026-02-10");
  private static final LocalDate OPEN_DAY = LocalDate.parse("2026-04-14");
  private static final LocalDate OTHER_OPEN_DAY = LocalDate.parse("2026-04-15");

  @Spy
  @InjectMocks
  private OvertimeService overtimeService;

  @Mock
  private EmployeecontractDAO employeecontractDAO;
  @Mock
  private PublicholidayDAO publicholidayDAO;
  @Mock
  private TimereportDAO timereportDAO;
  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private TimereportService timereportService;
  @Mock
  private EmployeeorderService employeeorderService;

  @BeforeEach
  void setUp() {
    var contract = new Employeecontract();
    ReflectionTestUtils.setField(contract, "id", EMPLOYEE_CONTRACT_ID);
    contract.setReportAcceptanceDate(ACCEPTANCE_DATE);
    when(employeecontractService.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
    // what the recalculation itself computes is not the question here, only whether it runs
    doNothing().when(overtimeService).updateOvertimeStatic(anyLong());
  }

  @Test
  void a_booking_moved_out_of_the_accepted_period_recalculates_the_static_overtime() {
    givenBookingOn(OPEN_DAY);

    overtimeService.onTimereportsCreatedOrUpdated(movedFrom(ACCEPTED_DAY));

    verify(overtimeService).updateOvertimeStatic(EMPLOYEE_CONTRACT_ID);
  }

  @Test
  void a_booking_moved_into_the_accepted_period_recalculates_the_static_overtime() {
    givenBookingOn(ACCEPTED_DAY);

    overtimeService.onTimereportsCreatedOrUpdated(movedFrom(OPEN_DAY));

    verify(overtimeService).updateOvertimeStatic(EMPLOYEE_CONTRACT_ID);
  }

  @Test
  void a_booking_moved_within_the_open_period_leaves_the_static_overtime_alone() {
    givenBookingOn(OTHER_OPEN_DAY);

    overtimeService.onTimereportsCreatedOrUpdated(movedFrom(OPEN_DAY));

    verify(overtimeService, never()).updateOvertimeStatic(anyLong());
  }

  @Test
  void a_booking_changed_on_its_open_day_leaves_the_static_overtime_alone() {
    givenBookingOn(OPEN_DAY);

    overtimeService.onTimereportsCreatedOrUpdated(new TimereportsCreatedOrUpdatedEvent(List.of(TIMEREPORT_ID)));

    verify(overtimeService, never()).updateOvertimeStatic(anyLong());
  }

  private void givenBookingOn(LocalDate day) {
    when(timereportService.getTimereportById(TIMEREPORT_ID)).thenReturn(TimereportDTO.builder()
        .id(TIMEREPORT_ID)
        .employeecontractId(EMPLOYEE_CONTRACT_ID)
        .referenceday(day)
        .build());
  }

  private static TimereportsCreatedOrUpdatedEvent movedFrom(LocalDate previousDay) {
    return new TimereportsCreatedOrUpdatedEvent(List.of(TIMEREPORT_ID), Map.of(TIMEREPORT_ID, previousDay));
  }

}
