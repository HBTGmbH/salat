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
import static de.hbt.salat.common.exception.ErrorCode.TR_SURCHARGE_HOLIDAY_ON_NON_HOLIDAY;
import static de.hbt.salat.common.exception.ErrorCode.TR_SURCHARGE_NOT_OFFERED;
import static de.hbt.salat.common.exception.ErrorCode.TR_SURCHARGE_WEEKEND_ON_WEEKDAY;
import static de.hbt.salat.order.domain.SurchargeKind.HOLIDAY;
import static de.hbt.salat.order.domain.SurchargeKind.NIGHT;
import static de.hbt.salat.order.domain.SurchargeKind.WEEKEND;

import java.time.DayOfWeek;
import java.time.Duration;
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
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.domain.Publicholiday;
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
import de.hbt.salat.order.domain.SurchargeKind;
import de.hbt.salat.order.domain.SurchargeRate;
import de.hbt.salat.order.persistence.EmployeeorderDAO;
import de.hbt.salat.testutils.EmployeeTestUtils;

/**
 * The time surcharge flag of a booking (#1469): only the kinds the suborder offers, weekend and holiday
 * only on such a day, the rate copied when the flag is set and kept while kind and suborder stay, the
 * credit duration × rate. Every write path ends in {@code createTimereports} or {@code updateTimereport},
 * so the rules are tested here once.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportServiceSurchargeTest {

  private static final long TIMEREPORT_ID = 3L;
  private static final long EMPLOYEE_CONTRACT_ID = 1L;
  private static final long EMPLOYEE_ORDER_ID = 2L;
  private static final long OTHER_EMPLOYEE_ORDER_ID = 4L;
  private static final String OWNER = EmployeeTestUtils.TESTY_SIGN;
  // the year must lie within one of the current year, otherwise the booking is rejected for that
  private static final int YEAR = Year.now().getValue();
  private static final LocalDate WEEKDAY = LocalDate.of(YEAR, 6, 15).with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY));
  private static final LocalDate SATURDAY = WEEKDAY.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
  private static final LocalTime TWENTY = LocalTime.of(20, 0);
  private static final LocalTime SIX = LocalTime.of(6, 0);

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
    customerorder.setSurchargeRate(NIGHT, new SurchargeRate(25, TWENTY, SIX));
    customerorder.setSurchargeRate(WEEKEND, SurchargeRate.of(50));
    customerorder.setSurchargeRate(HOLIDAY, SurchargeRate.of(100));
    suborder = suborder(10L, "1");
    otherSuborder = suborder(11L, "2");
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
  void a_night_booking_carries_the_rate_and_the_credit() {
    create(WEEKDAY, NIGHT, Duration.ofHours(4));

    var saved = saved();
    assertThat(saved.getSurchargeKind()).isEqualTo(NIGHT);
    assertThat(saved.getSurchargePercent()).isEqualTo(25);
    assertThat(saved.getSurchargeCredit()).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void a_weekend_booking_on_a_saturday() {
    create(SATURDAY, WEEKEND, Duration.ofHours(3));

    assertThat(saved().getSurchargeCredit()).isEqualTo(Duration.ofMinutes(90));
  }

  @Test
  void a_holiday_booking_on_a_public_holiday() {
    when(publicholidayDAO.getPublicHoliday(WEEKDAY)).thenReturn(Optional.of(new Publicholiday(WEEKDAY, "Feiertag")));

    create(WEEKDAY, HOLIDAY, Duration.ofHours(2));

    assertThat(saved().getSurchargeCredit()).isEqualTo(Duration.ofHours(2));
  }

  @Test
  void a_booking_without_a_flag_earns_nothing() {
    create(SATURDAY, null, Duration.ofHours(3));

    var saved = saved();
    assertThat(saved.getSurchargeKind()).isNull();
    assertThat(saved.getSurchargePercent()).isNull();
    assertThat(saved.getSurchargeCredit()).isZero();
  }

  @Test
  void the_rate_comes_from_the_suborder_after_inheritance() {
    suborder.setSurchargeRate(WEEKEND, SurchargeRate.of(75));

    create(SATURDAY, WEEKEND, Duration.ofHours(4));

    assertThat(saved().getSurchargePercent()).isEqualTo(75);
    assertThat(saved().getSurchargeCredit()).isEqualTo(Duration.ofHours(3));
  }

  @Test
  void a_kind_the_suborder_does_not_offer_is_refused() {
    suborder.setSurchargeRate(NIGHT, SurchargeRate.of(0));

    assertRefused(() -> create(WEEKDAY, NIGHT, Duration.ofHours(4)), TR_SURCHARGE_NOT_OFFERED);
  }

  @Test
  void standby_is_refused_a_surcharge() {
    suborder.setOrderType(OrderType.BEREITSCHAFT);

    assertRefused(() -> create(WEEKDAY, NIGHT, Duration.ofHours(4)), TR_SURCHARGE_NOT_OFFERED);
  }

  @Test
  void weekend_only_on_a_saturday_or_sunday() {
    assertRefused(() -> create(WEEKDAY, WEEKEND, Duration.ofHours(4)), TR_SURCHARGE_WEEKEND_ON_WEEKDAY);
  }

  @Test
  void holiday_only_on_a_public_holiday() {
    assertRefused(() -> create(SATURDAY, HOLIDAY, Duration.ofHours(4)), TR_SURCHARGE_HOLIDAY_ON_NON_HOLIDAY);
  }

  @Test
  void a_serial_booking_with_a_weekend_flag_is_refused_on_the_following_weekdays() {
    assertRefused(() -> timereportService.createTimereports(EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, SATURDAY, "Kommentar",
        List.of(), false, WEEKEND, Duration.ofHours(4), 2), TR_SURCHARGE_WEEKEND_ON_WEEKDAY);
  }

  @Test
  void a_serial_night_booking_flags_every_day() {
    timereportService.createTimereports(EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, WEEKDAY, "Kommentar",
        List.of(), false, NIGHT, Duration.ofHours(4), 2);

    var captor = ArgumentCaptor.forClass(Timereport.class);
    verify(timereportRepository, org.mockito.Mockito.times(2)).save(captor.capture());
    assertThat(captor.getAllValues()).allSatisfy(timereport -> {
      assertThat(timereport.getSurchargeKind()).isEqualTo(NIGHT);
      assertThat(timereport.getSurchargeCredit()).isEqualTo(Duration.ofHours(1));
    });
  }

  /** "Der Satz wird beim Speichern an der Buchung festgehalten": a later rate on the order reaches no booking. */
  @Test
  void a_changed_rate_on_the_order_leaves_an_edited_booking_at_its_stored_rate() {
    var timereport = givenBooking(WEEKDAY, NIGHT, 25, Duration.ofHours(4));
    customerorder.setSurchargeRate(NIGHT, new SurchargeRate(40, TWENTY, SIX));

    timereportService.updateTimereport(TIMEREPORT_ID, EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, WEEKDAY, "Kommentar",
        List.of(), false, NIGHT, Duration.ofHours(8));

    assertThat(timereport.getSurchargePercent()).isEqualTo(25);
    assertThat(timereport.getSurchargeCredit()).isEqualTo(Duration.ofHours(2));
  }

  @Test
  void another_kind_takes_the_rate_offered_now() {
    var timereport = givenBooking(SATURDAY, NIGHT, 25, Duration.ofHours(4));

    timereportService.updateTimereport(TIMEREPORT_ID, EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, SATURDAY, "Kommentar",
        List.of(), false, WEEKEND, Duration.ofHours(4));

    assertThat(timereport.getSurchargeKind()).isEqualTo(WEEKEND);
    assertThat(timereport.getSurchargePercent()).isEqualTo(50);
    assertThat(timereport.getSurchargeCredit()).isEqualTo(Duration.ofHours(2));
  }

  @Test
  void another_suborder_takes_its_rate() {
    var timereport = givenBooking(WEEKDAY, NIGHT, 25, Duration.ofHours(4));
    otherSuborder.setSurchargeRate(NIGHT, new SurchargeRate(50, TWENTY, SIX));

    timereportService.updateTimereport(TIMEREPORT_ID, EMPLOYEE_CONTRACT_ID, OTHER_EMPLOYEE_ORDER_ID, WEEKDAY, "Kommentar",
        List.of(), false, NIGHT, Duration.ofHours(4));

    assertThat(timereport.getSurchargePercent()).isEqualTo(50);
  }

  @Test
  void clearing_the_flag_drops_rate_and_credit() {
    var timereport = givenBooking(WEEKDAY, NIGHT, 25, Duration.ofHours(4));

    timereportService.updateTimereport(TIMEREPORT_ID, EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, WEEKDAY, "Kommentar",
        List.of(), false, null, Duration.ofHours(4));

    assertThat(timereport.getSurchargeKind()).isNull();
    assertThat(timereport.getSurchargePercent()).isNull();
    assertThat(timereport.getSurchargeCredit()).isZero();
  }

  /** The inline edit of the daily view, shifting and moving know nothing of the flag and keep it. */
  @Test
  void an_update_that_knows_nothing_of_the_flag_keeps_it_and_follows_the_duration() {
    var timereport = givenBooking(WEEKDAY, NIGHT, 25, Duration.ofHours(4));
    customerorder.setSurchargeRate(NIGHT, new SurchargeRate(40, TWENTY, SIX));

    timereportService.updateTimereport(TIMEREPORT_ID, EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, WEEKDAY, "neu", false,
        Duration.ofHours(2));

    assertThat(timereport.getSurchargeKind()).isEqualTo(NIGHT);
    assertThat(timereport.getSurchargePercent()).isEqualTo(25);
    assertThat(timereport.getSurchargeCredit()).isEqualTo(Duration.ofMinutes(30));
  }

  @Test
  void shifting_a_weekend_booking_to_a_weekday_is_refused() {
    givenBooking(SATURDAY, WEEKEND, 50, Duration.ofHours(4));

    assertRefused(() -> timereportService.shiftDays(TIMEREPORT_ID, -3), TR_SURCHARGE_WEEKEND_ON_WEEKDAY);
  }

  @Test
  void moving_a_flagged_booking_to_a_suborder_without_that_surcharge_is_refused() {
    givenBooking(WEEKDAY, NIGHT, 25, Duration.ofHours(4));
    otherSuborder.setSurchargeRate(NIGHT, SurchargeRate.of(0));
    when(authorizedUser.isManager()).thenReturn(true);

    assertRefused(() -> timereportService.updateTimereport(TIMEREPORT_ID, EMPLOYEE_CONTRACT_ID, OTHER_EMPLOYEE_ORDER_ID,
        WEEKDAY, "Kommentar", false, Duration.ofHours(4), true), TR_SURCHARGE_NOT_OFFERED);
  }

  @Test
  void the_kinds_of_a_day_holiday_before_weekend() {
    var sunday = SATURDAY.plusDays(1);
    when(publicholidayDAO.getPublicHoliday(sunday)).thenReturn(Optional.of(new Publicholiday(sunday, "Ostersonntag")));

    assertThat(timereportService.getSurchargeDayKinds(WEEKDAY)).isEmpty();
    assertThat(timereportService.getSurchargeDayKinds(SATURDAY)).containsExactly(WEEKEND);
    assertThat(timereportService.getSurchargeDayKinds(sunday)).containsExactly(HOLIDAY, WEEKEND);
  }

  private void create(LocalDate day, SurchargeKind kind, Duration duration) {
    timereportService.createTimereports(EMPLOYEE_CONTRACT_ID, EMPLOYEE_ORDER_ID, day, "Kommentar",
        List.of(), false, kind, duration, 1);
  }

  private void assertRefused(Runnable call, ErrorCode errorCode) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(BusinessRuleException.class, ex ->
            assertThat(ex.getMessages().getFirst().getErrorCode()).isEqualTo(errorCode));
    verify(timereportRepository, never()).save(any());
  }

  private Timereport saved() {
    var captor = ArgumentCaptor.forClass(Timereport.class);
    verify(timereportRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    return captor.getValue();
  }

  private Timereport givenBooking(LocalDate day, SurchargeKind kind, int percent, Duration duration) {
    var timereport = new Timereport();
    ReflectionTestUtils.setField(timereport, "id", TIMEREPORT_ID);
    timereport.setEmployeeorder(employeeorderDAO.getEmployeeorderById(EMPLOYEE_ORDER_ID));
    timereport.setReferenceday(referenceday(day));
    timereport.setTaskdescription("Kommentar");
    timereport.setDuration(duration);
    timereport.setSurcharge(kind, percent);
    timereport.setStatus(TIMEREPORT_STATUS_OPEN);
    when(timereportRepository.findById(TIMEREPORT_ID)).thenReturn(Optional.of(timereport));
    return timereport;
  }

  private Suborder suborder(long id, String sign) {
    var newSuborder = new Suborder();
    ReflectionTestUtils.setField(newSuborder, "id", id);
    newSuborder.setCustomerorder(customerorder);
    newSuborder.setSign(sign);
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
