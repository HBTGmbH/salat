package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_OF_OTHER_CONTRACT;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.domain.Referenceday;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.persistence.PublicholidayDAO;
import de.hbt.salat.dailyreport.persistence.ReferencedayRepository;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.persistence.TimereportRepository;
import de.hbt.salat.dailyreport.persistence.WorkingdayDAO;
import de.hbt.salat.employee.domain.Employee;
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
 * The contract of a booking is that of its employee order (#1210). The contract a caller names must
 * therefore be the one of the employee order it names — otherwise a favourite or a previous booking of
 * another contract would silently book for that contract. Every write path ends in
 * {@code createTimereports} or {@code updateTimereport}, so the check is tested here.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportServiceEmployeeorderContractTest {

  private static final long TIMEREPORT_ID = 3L;
  private static final long CONTRACT_ID = 1L;
  private static final long EARLIER_CONTRACT_ID = 5L;
  private static final long COLLEAGUE_CONTRACT_ID = 7L;
  private static final long EMPLOYEE_ORDER_ID = 2L;
  private static final long EARLIER_EMPLOYEE_ORDER_ID = 4L;
  private static final long COLLEAGUE_EMPLOYEE_ORDER_ID = 6L;
  private static final String OWNER = EmployeeTestUtils.TESTY_SIGN;
  private static final String COLLEAGUE = "col";
  // the year must lie within one of the current year, otherwise the booking is rejected for that
  private static final int YEAR = Year.now().getValue();
  private static final LocalDate DAY = LocalDate.of(YEAR, 6, 15);

  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private EmployeecontractDAO employeecontractDAO;
  @Mock
  private ReferencedayRepository referencedayRepository;
  @Mock
  private EmployeeorderDAO employeeorderDAO;
  @Mock
  private TimereportDAO timereportDAO;
  @Mock
  private TimereportRepository timereportRepository;
  @Mock
  private PublicholidayDAO publicholidayDAO;
  @Mock
  private WorkingdayDAO workingdayDAO;
  @Mock
  private AuthorizedUser authorizedUser;
  @Mock
  private AuthService authService;

  private TimereportService timereportService;
  private Employeecontract contract;
  private Suborder suborder;

  @BeforeEach
  void setUp() {
    timereportService = new TimereportService(eventPublisher, employeecontractDAO, referencedayRepository,
        employeeorderDAO, timereportDAO, timereportRepository, publicholidayDAO, workingdayDAO, authorizedUser,
        new TimereportAuthorization(authorizedUser, authService, mock(EmployeecontractService.class)));

    var owner = EmployeeTestUtils.createEmployee(OWNER);
    contract = contract(CONTRACT_ID, owner, LocalDate.of(YEAR - 1, 1, 1), null);
    var earlierContract = contract(EARLIER_CONTRACT_ID, owner, LocalDate.of(YEAR - 5, 1, 1),
        LocalDate.of(YEAR - 2, 12, 31));
    var colleagueContract = contract(COLLEAGUE_CONTRACT_ID, EmployeeTestUtils.createEmployee(COLLEAGUE),
        LocalDate.of(YEAR - 1, 1, 1), null);

    var customerorder = new Customerorder();
    customerorder.setSign("4711");
    customerorder.setOrderType(OrderType.STANDARD);
    suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign("01");
    employeeorder(EMPLOYEE_ORDER_ID, contract);
    employeeorder(EARLIER_EMPLOYEE_ORDER_ID, earlierContract);
    employeeorder(COLLEAGUE_EMPLOYEE_ORDER_ID, colleagueContract);

    when(referencedayRepository.findByRefdate(any())).thenAnswer(call -> Optional.of(referenceday(call.getArgument(0))));
    when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(any(), anyLong())).thenAnswer(call -> {
      var workingday = new Workingday();
      workingday.setRefday(call.getArgument(0));
      workingday.setStarttimehour(8);
      return workingday;
    });
    when(timereportRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(OWNER);
  }

  @Test
  void a_booking_takes_contract_and_suborder_of_its_employee_order() {
    create(EMPLOYEE_ORDER_ID);

    var captor = ArgumentCaptor.forClass(Timereport.class);
    verify(timereportRepository).save(captor.capture());
    assertThat(captor.getValue().getEmployeecontract()).isSameAs(contract);
    assertThat(captor.getValue().getSuborder()).isSameAs(suborder);
  }

  @Test
  void an_employee_order_of_another_person_is_refused() {
    assertThatThrownBy(() -> create(COLLEAGUE_EMPLOYEE_ORDER_ID))
        .isInstanceOfSatisfying(InvalidDataException.class, ex ->
            assertThat(ex.getMessages().getFirst().getErrorCode()).isEqualTo(TR_EMPLOYEE_ORDER_OF_OTHER_CONTRACT));
    verify(timereportRepository, never()).save(any());
  }

  /** A favourite from an earlier contract of the same person names an employee order of that contract. */
  @Test
  void an_employee_order_of_an_earlier_contract_of_the_same_person_is_refused() {
    assertThatThrownBy(() -> create(EARLIER_EMPLOYEE_ORDER_ID))
        .isInstanceOfSatisfying(InvalidDataException.class, ex ->
            assertThat(ex.getMessages().getFirst().getErrorCode()).isEqualTo(TR_EMPLOYEE_ORDER_OF_OTHER_CONTRACT));
    verify(timereportRepository, never()).save(any());
  }

  @Test
  void an_update_onto_an_employee_order_of_another_contract_is_refused() {
    givenBooking();

    assertThatThrownBy(() -> timereportService.updateTimereport(TIMEREPORT_ID, CONTRACT_ID,
        COLLEAGUE_EMPLOYEE_ORDER_ID, DAY, "Kommentar", false, 1, 0))
        .isInstanceOfSatisfying(InvalidDataException.class, ex ->
            assertThat(ex.getMessages().getFirst().getErrorCode()).isEqualTo(TR_EMPLOYEE_ORDER_OF_OTHER_CONTRACT));
    verify(timereportRepository, never()).save(any());
  }

  private void create(long employeeorderId) {
    timereportService.createTimereports(CONTRACT_ID, employeeorderId, DAY, "Kommentar", List.of(), false, 1, 0, 1);
  }

  private void givenBooking() {
    var timereport = new Timereport();
    ReflectionTestUtils.setField(timereport, "id", TIMEREPORT_ID);
    timereport.setEmployeeorder(employeeorderDAO.getEmployeeorderById(EMPLOYEE_ORDER_ID));
    timereport.setReferenceday(referenceday(DAY));
    timereport.setTaskdescription("Kommentar");
    timereport.setDurationhours(1);
    timereport.setDurationminutes(0);
    timereport.setStatus(TIMEREPORT_STATUS_OPEN);
    when(timereportRepository.findById(TIMEREPORT_ID)).thenReturn(Optional.of(timereport));
  }

  private Employeecontract contract(long id, Employee employee, LocalDate validFrom, LocalDate validUntil) {
    var newContract = new Employeecontract();
    ReflectionTestUtils.setField(newContract, "id", id);
    newContract.setEmployee(employee);
    newContract.setValidFrom(validFrom);
    newContract.setValidUntil(validUntil);
    when(employeecontractDAO.getEmployeecontractById(id)).thenReturn(newContract);
    return newContract;
  }

  private void employeeorder(long id, Employeecontract ofContract) {
    var employeeorder = new Employeeorder();
    ReflectionTestUtils.setField(employeeorder, "id", id);
    employeeorder.setEmployeecontract(ofContract);
    employeeorder.setSuborder(suborder);
    employeeorder.setFromDate(ofContract.getValidFrom());
    when(employeeorderDAO.getEmployeeorderById(id)).thenReturn(employeeorder);
  }

  private static Referenceday referenceday(LocalDate date) {
    var referenceday = new Referenceday();
    referenceday.setRefdate(date);
    return referenceday;
  }
}
