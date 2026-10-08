package de.hbt.salat.dailyreport.service;

import static java.time.DayOfWeek.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_CONTRACT_INVALID_REF_DATE;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_INVALID_REF_DATE;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.domain.Referenceday;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.persistence.PublicholidayDAO;
import de.hbt.salat.dailyreport.persistence.ReferencedayRepository;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.persistence.TimereportRepository;
import de.hbt.salat.dailyreport.persistence.WorkingdayDAO;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.persistence.EmployeecontractDAO;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.persistence.EmployeeorderDAO;
import de.hbt.salat.testutils.EmployeeTestUtils;

/**
 * Jeder Tag einer Serienbuchung muss im Gültigkeitszeitraum von Mitarbeiterauftrag und
 * Mitarbeitervertrag liegen (#1429). Bis dahin prüfte das Speichern nur den ersten Tag der Serie,
 * und die folgenden Tage wurden auch nach dem Ende des Auftrags oder Vertrags gebucht.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportServiceSerialValidityTest {

  private static final long EMPLOYEE_CONTRACT_ID = 1L;
  private static final long EMPLOYEE_ORDER_ID = 2L;
  private static final String OWNER = EmployeeTestUtils.TESTY_SIGN;

  // the year must lie within one of the current year, otherwise the booking is rejected for that
  private static final LocalDate MONDAY_IN_JUNE =
      LocalDate.of(Year.now().getValue(), 6, 1).with(TemporalAdjusters.nextOrSame(MONDAY));
  private static final LocalDate TUESDAY = MONDAY_IN_JUNE.plusDays(1);
  private static final LocalDate WEDNESDAY = MONDAY_IN_JUNE.plusDays(2);

  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private EmployeecontractDAO employeecontractDAO;
  @Mock private ReferencedayRepository referencedayRepository;
  @Mock private EmployeeorderDAO employeeorderDAO;
  @Mock private TimereportDAO timereportDAO;
  @Mock private TimereportRepository timereportRepository;
  @Mock private PublicholidayDAO publicholidayDAO;
  @Mock private WorkingdayDAO workingdayDAO;
  @Mock private AuthorizedUser authorizedUser;
  @Mock private AuthService authService;

  private TimereportService timereportService;
  private Employeecontract contract;
  private Employeeorder employeeorder;

  @BeforeEach
  void setUp() {
    timereportService = new TimereportService(eventPublisher, employeecontractDAO, referencedayRepository,
        employeeorderDAO, timereportDAO, timereportRepository, publicholidayDAO, workingdayDAO, authorizedUser,
        new TimereportAuthorization(authorizedUser, authService, mock(EmployeecontractService.class)));

    contract = new Employeecontract();
    ReflectionTestUtils.setField(contract, "id", EMPLOYEE_CONTRACT_ID);
    contract.setEmployee(EmployeeTestUtils.createEmployee(OWNER));
    contract.setValidFrom(MONDAY_IN_JUNE.minusYears(1));
    when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);

    var customerorder = new Customerorder();
    customerorder.setOrderType(OrderType.STANDARD);
    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    employeeorder = new Employeeorder();
    ReflectionTestUtils.setField(employeeorder, "id", EMPLOYEE_ORDER_ID);
    employeeorder.setEmployeecontract(contract);
    employeeorder.setSuborder(suborder);
    employeeorder.setFromDate(contract.getValidFrom());
    when(employeeorderDAO.getEmployeeorderById(EMPLOYEE_ORDER_ID)).thenReturn(employeeorder);

    when(publicholidayDAO.getPublicHoliday(any())).thenReturn(Optional.empty());
    when(referencedayRepository.findByRefdate(any())).thenAnswer(call -> Optional.of(referenceday(call.getArgument(0))));
    when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(any(), anyLong())).thenAnswer(call -> {
      var workingday = new Workingday();
      workingday.setRefday(call.getArgument(0));
      workingday.setStartTime(LocalTime.of(8, 0));
      return workingday;
    });
    when(timereportRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(OWNER);
  }

  @Test
  void a_series_running_past_the_end_of_the_employee_order_is_refused_as_a_whole() {
    employeeorder.setUntilDate(TUESDAY);

    assertThatThrownBy(() -> bookThreeDaysFrom(MONDAY_IN_JUNE))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(TR_EMPLOYEE_ORDER_INVALID_REF_DATE.getCode())
        .satisfies(refusal -> assertThat(argumentsOf(refusal)).containsExactly(DateUtils.format(WEDNESDAY)));
    verify(timereportRepository, never()).save(any());
  }

  @Test
  void a_series_running_past_the_end_of_the_employee_contract_is_refused_as_a_whole() {
    contract.setValidUntil(TUESDAY);

    assertThatThrownBy(() -> bookThreeDaysFrom(MONDAY_IN_JUNE))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(TR_EMPLOYEE_CONTRACT_INVALID_REF_DATE.getCode())
        .satisfies(refusal -> assertThat(argumentsOf(refusal)).containsExactly(DateUtils.format(WEDNESDAY)));
    verify(timereportRepository, never()).save(any());
  }

  @Test
  void a_series_within_the_validity_is_saved_day_by_day() {
    employeeorder.setUntilDate(WEDNESDAY);

    bookThreeDaysFrom(MONDAY_IN_JUNE);

    verify(timereportRepository, times(3)).save(any());
  }

  private void bookThreeDaysFrom(LocalDate day) {
    timereportService.createTimereports(EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, day, "comment", false, 1, 0, 3);
  }

  /** The message names the day that does not fit, so a series says which of its days is refused. */
  private static List<Object> argumentsOf(Throwable refusal) {
    return ((ErrorCodeException) refusal).getMessages().getFirst().getArguments();
  }

  private static Referenceday referenceday(LocalDate date) {
    var referenceday = new Referenceday();
    referenceday.setRefdate(date);
    return referenceday;
  }

}
