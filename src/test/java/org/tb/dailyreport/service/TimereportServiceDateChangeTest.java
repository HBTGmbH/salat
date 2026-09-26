package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static org.tb.common.exception.ErrorCode.TR_CLOSED_TIME_REPORT_REQ_MANAGER;
import static org.tb.common.exception.ErrorCode.TR_COMMITTED_TIME_REPORT_NOT_SELF;
import static org.tb.common.exception.ErrorCode.TR_COMMITTED_TIME_REPORT_REQ_MANAGER;

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
import org.tb.common.exception.AuthorizationException;
import org.tb.dailyreport.auth.TimereportAuthorization;
import org.tb.dailyreport.domain.Referenceday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.event.TimereportsCreatedOrUpdatedEvent;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.ReferencedayRepository;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.TimereportRepository;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.testutils.EmployeeTestUtils;

/**
 * Eine Buchung, deren Datum sich ändert, wird so behandelt, als würde sie am neuen Datum angelegt
 * (#1125). Ihr Status ergibt sich aus dem Zieldatum, und die Berechtigung gilt für den alten und den
 * neuen Stand. Bis dahin behielt sie den Status des alten Tags: eine Person konnte eine offene
 * Buchung in einen Zeitraum schieben, den sie schon freigegeben hatte, und dort weiter ändern.
 *
 * <p>Die Berechtigung läuft gegen die echte {@link TimereportAuthorization}: nur so ist gesichert,
 * dass die Ablehnung dieselbe Meldung trägt wie beim Anlegen.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportServiceDateChangeTest {

  private static final long TIMEREPORT_ID = 3L;
  private static final long EMPLOYEE_CONTRACT_ID = 1L;
  private static final long EMPLOYEE_ORDER_ID = 2L;
  private static final String OWNER = EmployeeTestUtils.TESTY_SIGN;
  private static final String MANAGER = EmployeeTestUtils.BOSS_SIGN;

  // the year must lie within one of the current year, otherwise the booking is rejected for that
  private static final int YEAR = Year.now().getValue();
  private static final LocalDate ACCEPTANCE_DATE = LocalDate.of(YEAR, 2, 28);
  private static final LocalDate RELEASE_DATE = LocalDate.of(YEAR, 3, 31);
  private static final LocalDate ACCEPTED_DAY = LocalDate.of(YEAR, 2, 10);
  private static final LocalDate OTHER_ACCEPTED_DAY = LocalDate.of(YEAR, 2, 11);
  private static final LocalDate RELEASED_DAY = LocalDate.of(YEAR, 3, 10);
  private static final LocalDate OTHER_RELEASED_DAY = LocalDate.of(YEAR, 3, 11);
  private static final LocalDate OPEN_DAY = LocalDate.of(YEAR, 4, 14);
  private static final LocalDate OTHER_OPEN_DAY = LocalDate.of(YEAR, 4, 15);
  private static final LocalDateTime RELEASED_AT = LocalDateTime.of(YEAR, 4, 1, 9, 0);
  private static final LocalDateTime ACCEPTED_AT = LocalDateTime.of(YEAR, 4, 2, 9, 0);

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
  private Employeeorder employeeorder;

  @BeforeEach
  void setUp() {
    timereportService = new TimereportService(eventPublisher, employeecontractDAO, referencedayRepository,
        employeeorderDAO, timereportDAO, timereportRepository, publicholidayDAO, workingdayDAO, authorizedUser,
        new TimereportAuthorization(authorizedUser, authService));

    contract = new Employeecontract();
    ReflectionTestUtils.setField(contract, "id", EMPLOYEE_CONTRACT_ID);
    contract.setEmployee(EmployeeTestUtils.createEmployee(OWNER));
    contract.setValidFrom(LocalDate.of(YEAR - 1, 1, 1));
    contract.setReportAcceptanceDate(ACCEPTANCE_DATE);
    contract.setReportReleaseDate(RELEASE_DATE);
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

    when(referencedayRepository.findByRefdate(any())).thenAnswer(call -> Optional.of(referenceday(call.getArgument(0))));
    when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(any(), anyLong())).thenAnswer(call -> {
      var workingday = new Workingday();
      workingday.setRefday(call.getArgument(0));
      workingday.setStarttimehour(8);
      return workingday;
    });
    when(timereportRepository.save(any())).thenAnswer(call -> call.getArgument(0));
  }

  @Test
  void a_person_moving_their_open_booking_into_the_released_period_is_refused_as_on_creation() {
    givenBooking(OPEN_DAY, TIMEREPORT_STATUS_OPEN);
    actingAsOwner();

    assertThatThrownBy(() -> moveTo(RELEASED_DAY))
        .isInstanceOf(AuthorizationException.class)
        .hasMessage(refusalOnCreation(RELEASED_DAY))
        .hasMessageContaining(TR_COMMITTED_TIME_REPORT_REQ_MANAGER.getCode());

    verify(timereportRepository, never()).save(any());
  }

  /** A manager or people lead passes the manager check and is stopped by the one against self-approval. */
  @Test
  void a_manager_moving_their_own_open_booking_into_the_released_period_is_refused_as_on_creation() {
    givenBooking(OPEN_DAY, TIMEREPORT_STATUS_OPEN);
    actingAsOwnerWhoIsManager();

    assertThatThrownBy(() -> moveTo(RELEASED_DAY))
        .isInstanceOf(AuthorizationException.class)
        .hasMessage(refusalOnCreation(RELEASED_DAY))
        .hasMessageContaining(TR_COMMITTED_TIME_REPORT_NOT_SELF.getCode());

    verify(timereportRepository, never()).save(any());
  }

  @Test
  void a_person_moving_their_open_booking_into_the_accepted_period_is_refused_as_on_creation() {
    givenBooking(OPEN_DAY, TIMEREPORT_STATUS_OPEN);
    actingAsOwner();

    assertThatThrownBy(() -> moveTo(ACCEPTED_DAY))
        .isInstanceOf(AuthorizationException.class)
        .hasMessage(refusalOnCreation(ACCEPTED_DAY))
        .hasMessageContaining(TR_CLOSED_TIME_REPORT_REQ_MANAGER.getCode());

    verify(timereportRepository, never()).save(any());
  }

  /** The old state has to be writable too: a released booking cannot be taken back by moving it out. */
  @Test
  void a_person_cannot_move_their_released_booking_into_the_open_period() {
    givenBooking(RELEASED_DAY, TIMEREPORT_STATUS_COMMITED);
    actingAsOwner();

    assertThatThrownBy(() -> moveTo(OPEN_DAY))
        .isInstanceOf(AuthorizationException.class)
        .hasMessageContaining(TR_COMMITTED_TIME_REPORT_REQ_MANAGER.getCode());

    verify(timereportRepository, never()).save(any());
  }

  @Test
  void a_manager_moving_an_accepted_booking_into_the_open_period_opens_it() {
    var timereport = givenBooking(ACCEPTED_DAY, TIMEREPORT_STATUS_CLOSED);
    actingAsManager();

    moveTo(OPEN_DAY);

    assertThat(timereport.getStatus()).isEqualTo(TIMEREPORT_STATUS_OPEN);
    // like a reopened booking, it no longer claims to have been released or accepted
    assertThat(timereport.getReleasedby()).isNull();
    assertThat(timereport.getReleased()).isNull();
    assertThat(timereport.getAcceptedby()).isNull();
    assertThat(timereport.getAccepted()).isNull();
    // the overtime account needs the day the booking left, since it is still part of overtimeStatic
    assertThat(publishedEvent().getPreviousReferencedays()).isEqualTo(Map.of(TIMEREPORT_ID, ACCEPTED_DAY));
  }

  @Test
  void a_manager_moving_an_accepted_booking_into_the_released_period_keeps_only_the_release() {
    var timereport = givenBooking(ACCEPTED_DAY, TIMEREPORT_STATUS_CLOSED);
    actingAsManager();

    moveTo(RELEASED_DAY);

    assertThat(timereport.getStatus()).isEqualTo(TIMEREPORT_STATUS_COMMITED);
    assertThat(timereport.getReleasedby()).isEqualTo(OWNER);
    assertThat(timereport.getReleased()).isEqualTo(RELEASED_AT);
    assertThat(timereport.getAcceptedby()).isNull();
    assertThat(timereport.getAccepted()).isNull();
  }

  @Test
  void a_manager_moving_an_open_booking_into_the_accepted_period_closes_it() {
    var timereport = givenBooking(OPEN_DAY, TIMEREPORT_STATUS_OPEN);
    actingAsManager();

    moveTo(ACCEPTED_DAY);

    assertThat(timereport.getStatus()).isEqualTo(TIMEREPORT_STATUS_CLOSED);
  }

  @Test
  void a_manager_moving_a_booking_within_the_same_period_changes_nothing_but_the_day() {
    var timereport = givenBooking(ACCEPTED_DAY, TIMEREPORT_STATUS_CLOSED);
    actingAsManager();

    moveTo(OTHER_ACCEPTED_DAY);

    assertThat(timereport.getReferenceday().getRefdate()).isEqualTo(OTHER_ACCEPTED_DAY);
    assertThat(timereport.getStatus()).isEqualTo(TIMEREPORT_STATUS_CLOSED);
    assertThat(timereport.getAcceptedby()).isEqualTo(MANAGER);
    assertThat(timereport.getAccepted()).isEqualTo(ACCEPTED_AT);
  }

  @Test
  void a_person_moving_their_open_booking_within_the_open_period_keeps_it_open() {
    var timereport = givenBooking(OPEN_DAY, TIMEREPORT_STATUS_OPEN);
    actingAsOwner();

    moveTo(OTHER_OPEN_DAY);

    assertThat(timereport.getStatus()).isEqualTo(TIMEREPORT_STATUS_OPEN);
    verify(timereportRepository).save(timereport);
  }

  @Test
  void a_manager_moving_a_released_booking_within_the_released_period_keeps_it_released() {
    var timereport = givenBooking(RELEASED_DAY, TIMEREPORT_STATUS_COMMITED);
    actingAsManager();

    moveTo(OTHER_RELEASED_DAY);

    assertThat(timereport.getStatus()).isEqualTo(TIMEREPORT_STATUS_COMMITED);
    assertThat(timereport.getReleasedby()).isEqualTo(OWNER);
  }

  /**
   * An open booking in the released period can exist from before the fix. Without a new day it is
   * edited as before; the next release sets it to committed.
   */
  @Test
  void a_change_without_a_new_day_leaves_the_status_alone() {
    var timereport = givenBooking(RELEASED_DAY, TIMEREPORT_STATUS_OPEN);
    actingAsOwner();

    timereportService.updateTimereport(TIMEREPORT_ID, EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, RELEASED_DAY,
        "changed comment", false, 2, 0);

    assertThat(timereport.getStatus()).isEqualTo(TIMEREPORT_STATUS_OPEN);
    assertThat(timereport.getDurationhours()).isEqualTo(2);
    assertThat(publishedEvent().getPreviousReferencedays()).isEmpty();
  }

  /** Moving bookings to another suborder ({@code MoveTimereportsService}) keeps the day and the status. */
  @Test
  void a_forced_update_on_the_same_day_keeps_the_status() {
    var timereport = givenBooking(ACCEPTED_DAY, TIMEREPORT_STATUS_CLOSED);
    actingAsManager();

    timereportService.updateTimereport(TIMEREPORT_ID, EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, ACCEPTED_DAY,
        "comment", false, 1, 0, true);

    assertThat(timereport.getStatus()).isEqualTo(TIMEREPORT_STATUS_CLOSED);
    verify(timereportRepository).save(timereport);
  }

  private Timereport givenBooking(LocalDate day, String status) {
    var timereport = new Timereport();
    ReflectionTestUtils.setField(timereport, "id", TIMEREPORT_ID);
    timereport.setEmployeecontract(contract);
    timereport.setEmployeeorder(employeeorder);
    timereport.setSuborder(employeeorder.getSuborder());
    timereport.setReferenceday(referenceday(day));
    timereport.setTaskdescription("comment");
    timereport.setDurationhours(1);
    timereport.setDurationminutes(0);
    timereport.setStatus(status);
    if (!TIMEREPORT_STATUS_OPEN.equals(status)) {
      timereport.setReleasedby(OWNER);
      timereport.setReleased(RELEASED_AT);
    }
    if (TIMEREPORT_STATUS_CLOSED.equals(status)) {
      timereport.setAcceptedby(MANAGER);
      timereport.setAccepted(ACCEPTED_AT);
    }
    when(timereportRepository.findById(TIMEREPORT_ID)).thenReturn(Optional.of(timereport));
    return timereport;
  }

  private void moveTo(LocalDate day) {
    timereportService.updateTimereport(TIMEREPORT_ID, EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, day,
        "comment", false, 1, 0);
  }

  private void actingAsOwner() {
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(OWNER);
    when(authorizedUser.isManager()).thenReturn(false);
  }

  private void actingAsOwnerWhoIsManager() {
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(OWNER);
    when(authorizedUser.isManager()).thenReturn(true);
  }

  private void actingAsManager() {
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(MANAGER);
    when(authorizedUser.isManager()).thenReturn(true);
  }

  /** what creating a booking on that day answers the same person */
  private String refusalOnCreation(LocalDate day) {
    var refusal = catchThrowable(() -> timereportService.createTimereports(EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, day,
        "comment", false, 1, 0, 1));
    assertThat(refusal).isInstanceOf(AuthorizationException.class);
    return refusal.getMessage();
  }

  private TimereportsCreatedOrUpdatedEvent publishedEvent() {
    var captor = ArgumentCaptor.forClass(TimereportsCreatedOrUpdatedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    return captor.getValue();
  }

  private static Referenceday referenceday(LocalDate date) {
    var referenceday = new Referenceday();
    referenceday.setRefdate(date);
    return referenceday;
  }

}
