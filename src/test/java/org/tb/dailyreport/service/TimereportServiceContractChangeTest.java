package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.common.exception.ErrorCode.TR_EMPLOYEE_CONTRACT_OTHER_EMPLOYEE;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Map;
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
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.common.exception.BusinessRuleException;
import org.tb.dailyreport.auth.TimereportAuthorization;
import org.tb.dailyreport.domain.Referenceday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.event.TimereportsCreatedOrUpdatedEvent;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.ReferencedayRepository;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.TimereportRepository;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.testutils.EmployeeTestUtils;

/**
 * Eine Buchung, die den Vertrag wechselt, wird so behandelt, als würde sie am neuen Vertrag angelegt
 * (#1128). Sie wechselt dabei den Vertrag, nie die Person, und das Ereignis nennt den Vertrag, den
 * sie verlassen hat — der Listener des Überstundenkontos kennt sonst nur den neuen.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportServiceContractChangeTest {

  private static final long TIMEREPORT_ID = 3L;
  private static final long FIRST_CONTRACT_ID = 1L;
  private static final long NEXT_CONTRACT_ID = 2L;
  private static final long OTHER_PERSONS_CONTRACT_ID = 9L;
  private static final long FIRST_ORDER_ID = 11L;
  private static final long NEXT_ORDER_ID = 12L;
  private static final long OTHER_PERSONS_ORDER_ID = 19L;
  private static final long EMPLOYEE_ID = 21L;
  private static final long OTHER_EMPLOYEE_ID = 29L;

  // the year must lie within one of the current year, otherwise the booking is rejected for that
  private static final int YEAR = Year.now().getValue();
  private static final LocalDate FIRST_CONTRACT_END = LocalDate.of(YEAR, 3, 31);
  private static final LocalDate NEXT_CONTRACT_BEGIN = LocalDate.of(YEAR, 4, 1);
  private static final LocalDate ACCEPTED_DAY = LocalDate.of(YEAR, 2, 10);
  private static final LocalDate OTHER_ACCEPTED_DAY = LocalDate.of(YEAR, 2, 11);
  private static final LocalDate DAY_IN_NEXT_CONTRACT = LocalDate.of(YEAR, 4, 14);
  private static final LocalDateTime RELEASED_AT = LocalDateTime.of(YEAR, 3, 1, 9, 0);
  private static final LocalDateTime ACCEPTED_AT = LocalDateTime.of(YEAR, 3, 2, 9, 0);

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
  private Employeecontract firstContract;
  private Employeeorder firstOrder;

  @BeforeEach
  void setUp() {
    timereportService = new TimereportService(eventPublisher, employeecontractDAO, referencedayRepository,
        employeeorderDAO, timereportDAO, timereportRepository, publicholidayDAO, workingdayDAO, authorizedUser,
        new TimereportAuthorization(authorizedUser, authService));

    var employee = employee(EmployeeTestUtils.TESTY_SIGN, EMPLOYEE_ID);
    firstContract = contract(FIRST_CONTRACT_ID, employee, LocalDate.of(YEAR - 1, 1, 1), FIRST_CONTRACT_END);
    firstContract.setReportAcceptanceDate(FIRST_CONTRACT_END);
    firstContract.setReportReleaseDate(FIRST_CONTRACT_END);
    firstOrder = employeeorder(FIRST_ORDER_ID, firstContract);

    var nextContract = contract(NEXT_CONTRACT_ID, employee, NEXT_CONTRACT_BEGIN, null);
    nextContract.setReportReleaseDate(LocalDate.of(YEAR, 4, 30));
    employeeorder(NEXT_ORDER_ID, nextContract);

    var otherPerson = employee("other", OTHER_EMPLOYEE_ID);
    var otherPersonsContract = contract(OTHER_PERSONS_CONTRACT_ID, otherPerson, LocalDate.of(YEAR - 1, 1, 1), null);
    employeeorder(OTHER_PERSONS_ORDER_ID, otherPersonsContract);

    when(referencedayRepository.findByRefdate(any())).thenAnswer(call -> Optional.of(referenceday(call.getArgument(0))));
    when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(any(), anyLong())).thenAnswer(call -> {
      var workingday = new Workingday();
      workingday.setRefday(call.getArgument(0));
      workingday.setStarttimehour(8);
      return workingday;
    });
    when(timereportRepository.save(any())).thenAnswer(call -> call.getArgument(0));

    // a manager who is not the booked person, so every status may be written
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(EmployeeTestUtils.BOSS_SIGN);
    when(authorizedUser.isManager()).thenReturn(true);
  }

  @Test
  void an_edit_onto_the_contract_of_another_person_is_refused() {
    givenAcceptedBooking(ACCEPTED_DAY);

    assertThatThrownBy(() -> update(OTHER_PERSONS_CONTRACT_ID, OTHER_PERSONS_ORDER_ID, ACCEPTED_DAY))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(TR_EMPLOYEE_CONTRACT_OTHER_EMPLOYEE.getCode());

    verify(timereportRepository, never()).save(any());
  }

  @Test
  void a_booking_moved_into_the_next_contract_takes_its_status_and_names_the_contract_it_left() {
    var timereport = givenAcceptedBooking(ACCEPTED_DAY);

    update(NEXT_CONTRACT_ID, NEXT_ORDER_ID, DAY_IN_NEXT_CONTRACT);

    assertThat(timereport.getStatus()).isEqualTo(TIMEREPORT_STATUS_COMMITED);
    assertThat(timereport.getAcceptedby()).isNull();
    assertThat(timereport.getAccepted()).isNull();
    assertThat(publishedEvent().getPreviousEmployeecontractIds()).isEqualTo(Map.of(TIMEREPORT_ID, FIRST_CONTRACT_ID));
    assertThat(publishedEvent().getPreviousReferencedays()).isEqualTo(Map.of(TIMEREPORT_ID, ACCEPTED_DAY));
  }

  @Test
  void a_booking_moved_within_its_contract_names_no_previous_contract() {
    var timereport = givenAcceptedBooking(ACCEPTED_DAY);

    update(FIRST_CONTRACT_ID, FIRST_ORDER_ID, OTHER_ACCEPTED_DAY);

    assertThat(timereport.getStatus()).isEqualTo(TIMEREPORT_STATUS_CLOSED);
    assertThat(publishedEvent().getPreviousEmployeecontractIds()).isEmpty();
  }

  @Test
  void the_contract_for_an_edit_is_the_one_of_the_booked_person_on_the_target_day() {
    givenVisibleBooking();
    var nextContract = employeecontractDAO.getEmployeecontractById(NEXT_CONTRACT_ID);
    when(employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(EMPLOYEE_ID, DAY_IN_NEXT_CONTRACT)).thenReturn(nextContract);

    assertThat(timereportService.getEmployeecontractIdForUpdate(TIMEREPORT_ID, DAY_IN_NEXT_CONTRACT))
        .isEqualTo(NEXT_CONTRACT_ID);
  }

  /** Ohne Vertrag am Zieldatum bleibt es beim eigenen; das Speichern meldet dann, dass er dort nicht gilt. */
  @Test
  void without_a_contract_on_the_target_day_the_booking_keeps_its_own() {
    givenVisibleBooking();

    assertThat(timereportService.getEmployeecontractIdForUpdate(TIMEREPORT_ID, LocalDate.of(YEAR - 2, 6, 1)))
        .isEqualTo(FIRST_CONTRACT_ID);
  }

  @Test
  void a_booking_the_user_cannot_see_yields_no_contract() {
    assertThat(timereportService.getEmployeecontractIdForUpdate(TIMEREPORT_ID, ACCEPTED_DAY)).isEqualTo(-1);
  }

  private Timereport givenAcceptedBooking(LocalDate day) {
    var timereport = new Timereport();
    ReflectionTestUtils.setField(timereport, "id", TIMEREPORT_ID);
    timereport.setEmployeecontract(firstContract);
    timereport.setEmployeeorder(firstOrder);
    timereport.setSuborder(firstOrder.getSuborder());
    timereport.setReferenceday(referenceday(day));
    timereport.setTaskdescription("comment");
    timereport.setDurationhours(1);
    timereport.setDurationminutes(0);
    timereport.setStatus(TIMEREPORT_STATUS_CLOSED);
    timereport.setReleasedby(EmployeeTestUtils.TESTY_SIGN);
    timereport.setReleased(RELEASED_AT);
    timereport.setAcceptedby(EmployeeTestUtils.BOSS_SIGN);
    timereport.setAccepted(ACCEPTED_AT);
    when(timereportRepository.findById(TIMEREPORT_ID)).thenReturn(Optional.of(timereport));
    return timereport;
  }

  /** what {@code TimereportDAO} hands out for a booking the user may read */
  private void givenVisibleBooking() {
    when(timereportDAO.getTimereportById(TIMEREPORT_ID)).thenReturn(TimereportDTO.builder()
        .id(TIMEREPORT_ID)
        .employeeId(EMPLOYEE_ID)
        .employeecontractId(FIRST_CONTRACT_ID)
        .referenceday(ACCEPTED_DAY)
        .build());
  }

  private void update(long contractId, long employeeorderId, LocalDate day) {
    timereportService.updateTimereport(TIMEREPORT_ID, contractId, employeeorderId, day, "comment", false, 1, 0);
  }

  private TimereportsCreatedOrUpdatedEvent publishedEvent() {
    var captor = ArgumentCaptor.forClass(TimereportsCreatedOrUpdatedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    return captor.getValue();
  }

  private static Employee employee(String sign, long id) {
    var employee = EmployeeTestUtils.createEmployee(sign);
    ReflectionTestUtils.setField(employee, "id", id);
    return employee;
  }

  private Employeecontract contract(long id, Employee employee, LocalDate validFrom, LocalDate validUntil) {
    var contract = new Employeecontract();
    ReflectionTestUtils.setField(contract, "id", id);
    contract.setEmployee(employee);
    contract.setValidFrom(validFrom);
    contract.setValidUntil(validUntil);
    when(employeecontractDAO.getEmployeecontractById(id)).thenReturn(contract);
    return contract;
  }

  private Employeeorder employeeorder(long id, Employeecontract contract) {
    var customerorder = new Customerorder();
    customerorder.setOrderType(OrderType.STANDARD);
    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    var employeeorder = new Employeeorder();
    ReflectionTestUtils.setField(employeeorder, "id", id);
    employeeorder.setEmployeecontract(contract);
    employeeorder.setSuborder(suborder);
    employeeorder.setFromDate(contract.getValidFrom());
    employeeorder.setUntilDate(contract.getValidUntil());
    when(employeeorderDAO.getEmployeeorderById(id)).thenReturn(employeeorder);
    return employeeorder;
  }

  private static Referenceday referenceday(LocalDate date) {
    var referenceday = new Referenceday();
    referenceday.setRefdate(date);
    return referenceday;
  }

}
