package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.customer.service.CustomerService;
import org.tb.dailyreport.auth.TimereportVisibility;
import org.tb.dailyreport.auth.TimereportVisibility.Clause;
import org.tb.dailyreport.auth.TimereportVisibilityService;
import org.tb.dailyreport.domain.TimereportFilterOptions.EmployeeOption;
import org.tb.dailyreport.domain.TimereportListFilter;
import org.tb.dailyreport.domain.TimereportListFilter.Billable;
import org.tb.dailyreport.domain.TimereportListFilter.Sort;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.TimereportListDAO;
import org.tb.dailyreport.persistence.TimereportListDAO.FilterValues;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;
import org.tb.jira.service.JiraTicketService;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * Die Namen im Filterblock des Ausdrucks (#1147). Die ids kommen aus der Anfrage; wer eine fremde id von Hand in die
 * URL schreibt, darf dafuer keinen Namen zurueckbekommen, den ihm die Auswahlliste nicht auch angeboten haette.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportListFilterSummaryTest {

  @Mock
  private TimereportListDAO timereportListDAO;
  @Mock
  private TimereportDAO timereportDAO;
  @Mock
  private TimereportVisibilityService visibilityService;
  @Mock
  private EmployeeService employeeService;
  @Mock
  private CustomerService customerService;
  @Mock
  private CustomerorderService customerorderService;
  @Mock
  private SuborderService suborderService;
  @Mock
  private JiraTicketService jiraTicketService;

  @InjectMocks
  private TimereportListService timereportListService;

  private final Employee visible = employee(7L, "Berta Beispiel", "bb");
  private final Employee foreign = employee(8L, "Anton Fremd", "af");

  @Test
  void names_every_chosen_employee_for_an_unrestricted_user_without_asking_the_bookings() {
    when(visibilityService.anyTime()).thenReturn(TimereportVisibility.all());
    when(employeeService.getEmployeesByIds(List.of(7L, 8L))).thenReturn(List.of(visible, foreign));

    var summary = timereportListService.describe(filterFor(List.of(7L, 8L)));

    assertThat(summary.employees()).extracting(EmployeeOption::sign).containsExactly("af", "bb");
    verify(timereportListDAO, never()).findFilterValues(any());
  }

  @Test
  void leaves_out_an_employee_whose_bookings_the_user_may_not_read() {
    var visibility = TimereportVisibility.of(List.of(Clause.forEmployees(Set.of(7L))));
    when(visibilityService.anyTime()).thenReturn(visibility);
    when(timereportListDAO.findFilterValues(visibility))
        .thenReturn(new FilterValues(List.of(7L), List.of(), List.of(), List.of()));
    when(employeeService.getEmployeesByIds(List.of(7L))).thenReturn(List.of(visible));

    var summary = timereportListService.describe(filterFor(List.of(7L, 8L)));

    assertThat(summary.employees()).extracting(EmployeeOption::name).containsExactly("Berta Beispiel");
  }

  @Test
  void does_not_compute_the_visibility_without_an_employee_filter() {
    var summary = timereportListService.describe(filterFor(List.of()));

    assertThat(summary.employees()).isEmpty();
    verify(visibilityService, never()).anyTime();
  }

  private static TimereportListFilter filterFor(List<Long> employeeIds) {
    return new TimereportListFilter(employeeIds, List.of(), List.of(), List.of(), List.of(), true,
        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), Billable.ALL, Sort.DATE, false,
        TimereportListFilter.UNLIMITED);
  }

  private static Employee employee(long id, String name, String sign) {
    var created = new Employee();
    setField(created, "id", id);
    created.setFirstname(name.split(" ")[0]);
    created.setLastname(name.split(" ")[1]);
    created.setSign(sign);
    return created;
  }
}
