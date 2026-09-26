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
 * Eine Buchung, die den Vertrag wechselt, kann den abgenommenen Zeitraum ihres alten Vertrags
 * verlassen (#1128). Der Listener liest den Vertrag aus der Buchung, kennt also nur den neuen; den
 * alten nennt das Ereignis. Der Tag, den die Buchung verlassen hat, gehört dabei zum alten Vertrag,
 * nicht zum neuen.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class OvertimeServiceContractChangeTest {

  private static final long TIMEREPORT_ID = 3L;
  private static final long OLD_CONTRACT_ID = 1L;
  private static final long NEW_CONTRACT_ID = 2L;

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
    // what the recalculation itself computes is not the question here, only whether it runs
    doNothing().when(overtimeService).updateOvertimeStatic(anyLong());
  }

  @Test
  void a_booking_leaving_the_accepted_period_of_its_old_contract_recalculates_that_contract() {
    givenContract(OLD_CONTRACT_ID, "2026-03-31");
    givenContract(NEW_CONTRACT_ID, null);
    givenBookingOn(NEW_CONTRACT_ID, "2026-04-14");

    overtimeService.onTimereportsCreatedOrUpdated(movedFrom(OLD_CONTRACT_ID, "2026-03-30"));

    verify(overtimeService).updateOvertimeStatic(OLD_CONTRACT_ID);
    verify(overtimeService, never()).updateOvertimeStatic(NEW_CONTRACT_ID);
  }

  @Test
  void a_booking_arriving_in_the_accepted_period_of_its_new_contract_recalculates_both_where_both_are_accepted() {
    givenContract(OLD_CONTRACT_ID, "2026-03-31");
    givenContract(NEW_CONTRACT_ID, "2026-04-30");
    givenBookingOn(NEW_CONTRACT_ID, "2026-04-14");

    overtimeService.onTimereportsCreatedOrUpdated(movedFrom(OLD_CONTRACT_ID, "2026-03-30"));

    verify(overtimeService).updateOvertimeStatic(OLD_CONTRACT_ID);
    verify(overtimeService).updateOvertimeStatic(NEW_CONTRACT_ID);
  }

  /** The day the booking left lies in the accepted period of the new contract — but it did not leave that one. */
  @Test
  void the_day_a_booking_left_is_weighed_against_the_contract_it_left() {
    givenContract(OLD_CONTRACT_ID, "2026-01-31");
    givenContract(NEW_CONTRACT_ID, "2026-03-31");
    givenBookingOn(NEW_CONTRACT_ID, "2026-04-14");

    overtimeService.onTimereportsCreatedOrUpdated(movedFrom(OLD_CONTRACT_ID, "2026-02-10"));

    verify(overtimeService, never()).updateOvertimeStatic(anyLong());
  }

  /** Umbuchen: gleicher Vertrag, gleicher Tag. Ob der Zielauftrag als Arbeitszeit zählt, entscheidet die Summe. */
  @Test
  void a_booking_changed_on_its_accepted_day_recalculates_its_contract() {
    givenContract(OLD_CONTRACT_ID, "2026-03-31");
    givenBookingOn(OLD_CONTRACT_ID, "2026-03-30");

    overtimeService.onTimereportsCreatedOrUpdated(new TimereportsCreatedOrUpdatedEvent(List.of(TIMEREPORT_ID)));

    verify(overtimeService).updateOvertimeStatic(OLD_CONTRACT_ID);
  }

  private void givenContract(long id, String acceptanceDate) {
    var contract = new Employeecontract();
    ReflectionTestUtils.setField(contract, "id", id);
    contract.setReportAcceptanceDate(acceptanceDate == null ? null : LocalDate.parse(acceptanceDate));
    when(employeecontractService.getEmployeecontractById(id)).thenReturn(contract);
  }

  private void givenBookingOn(long contractId, String day) {
    when(timereportService.getTimereportById(TIMEREPORT_ID)).thenReturn(TimereportDTO.builder()
        .id(TIMEREPORT_ID)
        .employeecontractId(contractId)
        .referenceday(LocalDate.parse(day))
        .build());
  }

  private static TimereportsCreatedOrUpdatedEvent movedFrom(long contractId, String day) {
    return new TimereportsCreatedOrUpdatedEvent(List.of(TIMEREPORT_ID),
        Map.of(TIMEREPORT_ID, LocalDate.parse(day)), Map.of(TIMEREPORT_ID, contractId));
  }

}
