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
import static de.hbt.salat.common.exception.ErrorCode.XX_CONCURRENT_MODIFICATION;

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
import de.hbt.salat.order.persistence.EmployeeorderDAO;
import de.hbt.salat.testutils.EmployeeTestUtils;

/**
 * What a caller outside the form needs from saving (#1532): the ids of the bookings created, and a change refused
 * when the booking was changed since the caller read it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportServiceVersionAndIdsTest {

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
  void creating_returns_the_ids_of_the_bookings_created() {
    when(timereportRepository.save(any())).thenAnswer(call -> {
      Timereport timereport = call.getArgument(0);
      ReflectionTestUtils.setField(timereport, "id", 41L);
      return timereport;
    });

    var ids = timereportService.createTimereports(EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, DAY, "Kommentar",
        List.of(), false, Duration.ofHours(1), 1);

    assertThat(ids).containsExactly(41L);
  }

  @Test
  void a_change_with_the_version_read_saves() {
    var timereport = givenBooking();
    ReflectionTestUtils.setField(timereport, "updatecounter", 3);

    timereportService.updateTimereport(TIMEREPORT_ID, 3, EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, DAY, "neu",
        List.of(), false, null, Duration.ofHours(2));

    assertThat(saved().getDuration()).isEqualTo(Duration.ofHours(2));
  }

  @Test
  void a_change_to_a_booking_changed_since_it_was_read_is_refused() {
    var timereport = givenBooking();
    ReflectionTestUtils.setField(timereport, "updatecounter", 4);

    assertThatThrownBy(() -> timereportService.updateTimereport(TIMEREPORT_ID, 3, EMPLOYEE_CONTRACT_ID,
        EMPLOYEE_ORDER_ID, DAY, "neu", List.of(), false, null, Duration.ofHours(2)))
        .isInstanceOfSatisfying(BusinessRuleException.class, ex ->
            assertThat(ex.getMessages().getFirst().getErrorCode()).isEqualTo(XX_CONCURRENT_MODIFICATION));
    verify(timereportRepository, never()).save(any());
    assertThat(timereport.getDuration()).isEqualTo(Duration.ofHours(1));
  }

  private Timereport saved() {
    var captor = ArgumentCaptor.forClass(Timereport.class);
    verify(timereportRepository).save(captor.capture());
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
