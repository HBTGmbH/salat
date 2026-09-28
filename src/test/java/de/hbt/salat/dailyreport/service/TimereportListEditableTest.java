package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import de.hbt.salat.customer.service.CustomerService;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.auth.TimereportVisibility;
import de.hbt.salat.dailyreport.auth.TimereportVisibilityService;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.dailyreport.domain.TimereportListFilter;
import de.hbt.salat.dailyreport.domain.TimereportListFilter.Billable;
import de.hbt.salat.dailyreport.domain.TimereportListFilter.Sort;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO.Totals;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.jira.service.JiraTicketService;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * Which rows the booking list offers to edit (#1190). The rule itself — who may write which status — is
 * {@link TimereportAuthorization#isWriteAllowed} and tested there; this test holds the list to asking exactly that, and
 * to asking it once per contract and status rather than once per row.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class TimereportListEditableTest {

  @Mock private TimereportListDAO timereportListDAO;
  @Mock private TimereportDAO timereportDAO;
  @Mock private TimereportVisibilityService visibilityService;
  @Mock private TimereportAuthorization timereportAuthorization;
  @Mock private EmployeeService employeeService;
  @Mock private CustomerService customerService;
  @Mock private CustomerorderService customerorderService;
  @Mock private SuborderService suborderService;
  @Mock private JiraTicketService jiraTicketService;

  @InjectMocks
  private TimereportListService timereportListService;

  private final Employeecontract own = contract(1L);
  private final Employeecontract other = contract(2L);

  @BeforeEach
  void setUp() {
    when(visibilityService.forPeriod(any())).thenReturn(TimereportVisibility.all());
    when(timereportListDAO.findTotals(any(), any()))
        .thenReturn(new Totals(5, Duration.ofHours(5), Duration.ZERO, 2, 1));
  }

  @Test
  void a_row_is_editable_exactly_where_the_status_rule_allows_writing() {
    when(timereportListDAO.findRows(any(), any())).thenReturn(List.of(
        booking(11L, own, TIMEREPORT_STATUS_OPEN),
        booking(12L, own, TIMEREPORT_STATUS_COMMITED),
        booking(13L, other, TIMEREPORT_STATUS_OPEN),
        booking(14L, other, TIMEREPORT_STATUS_CLOSED)));
    when(timereportAuthorization.isWriteAllowed(own, TIMEREPORT_STATUS_OPEN)).thenReturn(true);
    when(timereportAuthorization.isWriteAllowed(own, TIMEREPORT_STATUS_COMMITED)).thenReturn(false);
    when(timereportAuthorization.isWriteAllowed(other, TIMEREPORT_STATUS_OPEN)).thenReturn(false);
    when(timereportAuthorization.isWriteAllowed(other, TIMEREPORT_STATUS_CLOSED)).thenReturn(true);

    var result = timereportListService.search(filter());

    assertThat(result.editableIds()).containsExactlyInAnyOrder(11L, 14L);
    assertThat(result.isEditable(12L)).isFalse();
  }

  @Test
  void the_rule_is_asked_once_per_contract_and_status_not_once_per_row() {
    when(timereportListDAO.findRows(any(), any())).thenReturn(List.of(
        booking(11L, own, TIMEREPORT_STATUS_OPEN),
        booking(12L, own, TIMEREPORT_STATUS_OPEN),
        booking(13L, own, TIMEREPORT_STATUS_OPEN),
        booking(14L, other, TIMEREPORT_STATUS_OPEN),
        booking(15L, own, TIMEREPORT_STATUS_COMMITED)));
    when(timereportAuthorization.isWriteAllowed(any(), any())).thenReturn(true);

    var result = timereportListService.search(filter());

    assertThat(result.editableIds()).hasSize(5);
    verify(timereportAuthorization, times(1)).isWriteAllowed(own, TIMEREPORT_STATUS_OPEN);
    verify(timereportAuthorization, times(1)).isWriteAllowed(other, TIMEREPORT_STATUS_OPEN);
    verify(timereportAuthorization, times(1)).isWriteAllowed(own, TIMEREPORT_STATUS_COMMITED);
  }

  @Test
  void without_hits_nothing_is_asked() {
    when(timereportListDAO.findTotals(any(), any())).thenReturn(Totals.none());

    var result = timereportListService.search(filter());

    assertThat(result.editableIds()).isEmpty();
    verify(timereportAuthorization, never()).isWriteAllowed(any(), any());
  }

  private static TimereportListFilter filter() {
    return new TimereportListFilter(List.of(), List.of(), List.of(), List.of(), List.of(), false,
        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), Billable.ALL, Sort.DATE, false, 500);
  }

  private static Employeecontract contract(long id) {
    var contract = new Employeecontract();
    setField(contract, "id", id);
    return contract;
  }

  private static Timereport booking(long id, Employeecontract contract, String status) {
    var timereport = new Timereport();
    setField(timereport, "id", id);
    timereport.setEmployeecontract(contract);
    timereport.setStatus(status);
    return timereport;
  }

}
