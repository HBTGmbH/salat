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
import static de.hbt.salat.common.exception.ErrorCode.TR_TICKET_REFERENCES_EXCEED_LIMIT;
import static de.hbt.salat.common.exception.ErrorCode.TR_TICKET_REFERENCES_NOT_ALLOWED;
import static de.hbt.salat.common.exception.ErrorCode.TR_TICKET_REFERENCE_DUPLICATE;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
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
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCodeException;
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
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.persistence.EmployeecontractDAO;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.TicketReferenceMode;
import de.hbt.salat.order.domain.TicketReferencePolicy;
import de.hbt.salat.order.persistence.EmployeeorderDAO;
import de.hbt.salat.testutils.EmployeeTestUtils;

/**
 * As many ticket references as the suborder allows, checked on every save (#1326). Every write path
 * ends in {@code createTimereports} or {@code updateTimereport} — form, inline edit, REST, CSV import,
 * favourite, previous booking, shifting and moving to another suborder — so the check is tested here
 * once, and the paths are tested for handing the references through.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportServiceTicketReferenceLimitTest {

  private static final long TIMEREPORT_ID = 3L;
  private static final long EMPLOYEE_CONTRACT_ID = 1L;
  private static final long EMPLOYEE_ORDER_ID = 2L;
  private static final long OTHER_EMPLOYEE_ORDER_ID = 4L;
  private static final String OWNER = EmployeeTestUtils.TESTY_SIGN;
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
  private Customerorder customerorder;
  private Suborder parent;
  private Suborder suborder;
  private Suborder otherSuborder;

  @BeforeEach
  void setUp() {
    timereportService = new TimereportService(eventPublisher, employeecontractDAO, referencedayRepository,
        employeeorderDAO, timereportDAO, timereportRepository, publicholidayDAO, workingdayDAO, authorizedUser,
        new TimereportAuthorization(authorizedUser, authService, mock(EmployeecontractService.class)));

    contract = new Employeecontract();
    ReflectionTestUtils.setField(contract, "id", EMPLOYEE_CONTRACT_ID);
    contract.setEmployee(EmployeeTestUtils.createEmployee(OWNER));
    contract.setValidFrom(LocalDate.of(YEAR - 1, 1, 1));
    when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);

    customerorder = new Customerorder();
    customerorder.setSign("4711");
    customerorder.setOrderType(OrderType.STANDARD);
    parent = suborder("20", null);
    suborder = suborder("1", parent);
    otherSuborder = suborder("30", null);
    employeeorder(EMPLOYEE_ORDER_ID, suborder);
    employeeorder(OTHER_EMPLOYEE_ORDER_ID, otherSuborder);

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
  void an_order_allows_any_number_by_default() {
    create("ABC-1", "ABC-2", "ABC-3");

    assertThat(saved().getTicketReferences()).containsExactly("ABC-1", "ABC-2", "ABC-3");
  }

  @Test
  void more_references_than_allowed_are_refused_naming_the_number() {
    customerorder.setTicketReferencePolicy(new TicketReferencePolicy(TicketReferenceMode.LIMITED, 1));

    assertThatThrownBy(() -> create("ABC-1", "ABC-2"))
        .isInstanceOfSatisfying(BusinessRuleException.class, ex -> {
          assertThat(ex.getMessages().getFirst().getErrorCode()).isEqualTo(TR_TICKET_REFERENCES_EXCEED_LIMIT);
          assertThat(ex.getMessages().getFirst().getArguments()).containsExactly("4711/20/1", 1, DAY.toString(), 2);
        });
    verify(timereportRepository, never()).save(any());
  }

  @Test
  void a_suborder_allowing_none_refuses_every_reference() {
    suborder.setTicketReferencePolicy(new TicketReferencePolicy(TicketReferenceMode.NONE, null));

    assertThatThrownBy(() -> create("ABC-1"))
        .isInstanceOfSatisfying(BusinessRuleException.class, ex ->
            assertThat(ex.getMessages().getFirst().getErrorCode()).isEqualTo(TR_TICKET_REFERENCES_NOT_ALLOWED));
    verify(timereportRepository, never()).save(any());
  }

  @Test
  void a_suborder_allowing_none_still_books_without_references() {
    suborder.setTicketReferencePolicy(new TicketReferencePolicy(TicketReferenceMode.NONE, null));

    create();

    assertThat(saved().getTicketReferences()).isEmpty();
  }

  @Test
  void the_setting_is_inherited_from_the_suborder_above() {
    customerorder.setTicketReferencePolicy(new TicketReferencePolicy(TicketReferenceMode.NONE, null));
    parent.setTicketReferencePolicy(new TicketReferencePolicy(TicketReferenceMode.LIMITED, 2));

    assertThatThrownBy(() -> create("ABC-1", "ABC-2", "ABC-3")).isInstanceOf(BusinessRuleException.class);
    create("ABC-1", "ABC-2");
    assertThat(saved().getTicketReferences()).containsExactly("ABC-1", "ABC-2");
  }

  @Test
  void the_own_setting_wins_over_the_inherited_one() {
    parent.setTicketReferencePolicy(new TicketReferencePolicy(TicketReferenceMode.UNLIMITED, null));
    suborder.setTicketReferencePolicy(new TicketReferencePolicy(TicketReferenceMode.LIMITED, 2));

    assertThatThrownBy(() -> create("ABC-1", "ABC-2", "ABC-3")).isInstanceOf(BusinessRuleException.class);
    create("ABC-1", "ABC-2");
    assertThat(saved().getTicketReferences()).containsExactly("ABC-1", "ABC-2");
  }

  @Test
  void references_are_stored_normalized_and_a_duplicate_is_refused() {
    customerorder.setTicketReferencePolicy(new TicketReferencePolicy(TicketReferenceMode.UNLIMITED, null));

    create(" abc-1 ", "Retro");
    assertThat(saved().getTicketReferences()).containsExactly("ABC-1", "Retro");

    assertThatThrownBy(() -> create("ABC-1", "abc-1"))
        .isInstanceOfSatisfying(InvalidDataException.class, ex ->
            assertThat(ex.getMessages().getFirst().getErrorCode()).isEqualTo(TR_TICKET_REFERENCE_DUPLICATE));
  }

  /**
   * "Immer": a booking that no longer fits a stricter setting is refused even where the references do
   * not change - shifting, the inline edit of the comment, moving. Only fewer references save it.
   */
  @Test
  void an_update_leaving_the_references_alone_is_refused_once_the_setting_is_stricter() {
    givenBooking("ABC-1", "ABC-2");
    customerorder.setTicketReferencePolicy(new TicketReferencePolicy(TicketReferenceMode.LIMITED, 1));

    assertThatThrownBy(() -> timereportService.updateTimereport(TIMEREPORT_ID, EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID,
        DAY, "neuer Kommentar", false, 1, 0))
        .isInstanceOfSatisfying(BusinessRuleException.class, ex ->
            assertThat(ex.getMessages().getFirst().getErrorCode()).isEqualTo(TR_TICKET_REFERENCES_EXCEED_LIMIT));
    verify(timereportRepository, never()).save(any());
  }

  @Test
  void an_update_with_fewer_references_saves() {
    var timereport = givenBooking("ABC-1", "ABC-2");
    customerorder.setTicketReferencePolicy(new TicketReferencePolicy(TicketReferenceMode.LIMITED, 1));

    timereportService.updateTimereport(TIMEREPORT_ID, EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, DAY, "Kommentar",
        List.of("ABC-2"), false, 1, 0);

    assertThat(timereport.getTicketReferences()).containsExactly("ABC-2");
  }

  @Test
  void an_update_without_references_keeps_those_stored() {
    var timereport = givenBooking("ABC-1");

    timereportService.updateTimereport(TIMEREPORT_ID, EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, DAY, "neu", false, 1, 0);

    assertThat(timereport.getTicketReferences()).containsExactly("ABC-1");
  }

  /** Moving bookings to another suborder ({@code MoveTimereportsService}) is a forced update. */
  @Test
  void moving_a_booking_to_a_suborder_without_references_is_refused() {
    givenBooking("ABC-1");
    otherSuborder.setTicketReferencePolicy(new TicketReferencePolicy(TicketReferenceMode.NONE, null));
    when(authorizedUser.isManager()).thenReturn(true);

    assertThatThrownBy(() -> timereportService.updateTimereport(TIMEREPORT_ID, EMPLOYEE_CONTRACT_ID,
        OTHER_EMPLOYEE_ORDER_ID, DAY, "Kommentar", false, 1, 0, true))
        .isInstanceOfSatisfying(ErrorCodeException.class, ex ->
            assertThat(ex.getMessages().getFirst().getErrorCode()).isEqualTo(TR_TICKET_REFERENCES_NOT_ALLOWED));
    verify(timereportRepository, never()).save(any());
  }

  @Test
  void the_policy_of_a_booking_is_that_of_its_suborder_after_inheritance() {
    parent.setTicketReferencePolicy(new TicketReferencePolicy(TicketReferenceMode.LIMITED, 3));
    givenBooking();

    assertThat(timereportService.getTicketReferencePolicy(TIMEREPORT_ID))
        .isEqualTo(new TicketReferencePolicy(TicketReferenceMode.LIMITED, 3));
  }

  private void create(String... references) {
    timereportService.createTimereports(EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, DAY, "Kommentar",
        List.of(references), false, 1, 0, 1);
  }

  private Timereport saved() {
    var captor = ArgumentCaptor.forClass(Timereport.class);
    verify(timereportRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    return captor.getValue();
  }

  private Timereport givenBooking(String... references) {
    var timereport = new Timereport();
    ReflectionTestUtils.setField(timereport, "id", TIMEREPORT_ID);
    timereport.setEmployeeorder(employeeorderDAO.getEmployeeorderById(EMPLOYEE_ORDER_ID));
    timereport.setReferenceday(referenceday(DAY));
    timereport.setTaskdescription("Kommentar");
    timereport.setDuration(Duration.ofHours(1));
    timereport.setStatus(TIMEREPORT_STATUS_OPEN);
    timereport.setTicketReferences(List.of(references));
    when(timereportRepository.findById(TIMEREPORT_ID)).thenReturn(Optional.of(timereport));
    return timereport;
  }

  private Suborder suborder(String sign, Suborder parentSuborder) {
    var newSuborder = new Suborder();
    newSuborder.setCustomerorder(customerorder);
    newSuborder.setSign(sign);
    newSuborder.setParentorder(parentSuborder);
    newSuborder.deriveCompleteOrderSign();
    return newSuborder;
  }

  private void employeeorder(long id, Suborder onSuborder) {
    var employeeorder = new Employeeorder();
    ReflectionTestUtils.setField(employeeorder, "id", id);
    employeeorder.setEmployeecontract(contract);
    employeeorder.setSuborder(onSuborder);
    employeeorder.setFromDate(contract.getValidFrom());
    when(employeeorderDAO.getEmployeeorderById(id)).thenReturn(employeeorder);
  }

  private static Referenceday referenceday(LocalDate date) {
    var referenceday = new Referenceday();
    referenceday.setRefdate(date);
    return referenceday;
  }
}
