package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.common.exception.ErrorCode.TR_ABSENCE_DAY_EXCEEDS_TARGET;
import static de.hbt.salat.common.exception.ErrorCode.TR_ABSENCE_DAY_WITHOUT_TARGET;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static de.hbt.salat.common.util.DateUtils.format;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
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
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
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
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SurchargeKind;
import de.hbt.salat.order.persistence.EmployeeorderDAO;
import de.hbt.salat.testutils.EmployeeTestUtils;

/**
 * An einem Tag mit Urlaub, Krankheit oder einer anderen Abwesenheit dürfen alle Buchungen zusammen die
 * Tagesarbeitszeit nicht überschreiten (#397) — geprüft beim Speichern jeder Buchung, von beiden Seiten aus,
 * beim Anlegen, Ändern, Verschieben auf einen anderen Tag und auf einen anderen Unterauftrag. Ein Tag ohne
 * Abwesenheit bleibt von dieser Regel unberührt. Tage, die die Regel schon vorher verletzt haben, bleiben
 * änderbar, solange die Änderung die Überschreitung nicht vergrößert.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportServiceAbsenceDayLimitTest {

  private static final long EMPLOYEE_CONTRACT_ID = 1L;
  private static final long WORK_ORDER_ID = 2L;
  private static final long VACATION_ORDER_ID = 3L;
  private static final long SICKNESS_ORDER_ID = 4L;
  private static final long STANDBY_ORDER_ID = 5L;
  private static final String OWNER = EmployeeTestUtils.TESTY_SIGN;
  private static final Duration DAILY_WORKING_TIME = Duration.ofHours(8);

  // the year must lie within one of the current year, otherwise the booking is rejected for that
  private static final int YEAR = Year.now().getValue();
  private static final LocalDate MONDAY = LocalDate.of(YEAR, 3, 2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
  private static final LocalDate TUESDAY = MONDAY.plusDays(1);
  private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
  private static final LocalDate SATURDAY = MONDAY.plusDays(5);

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

  private TimereportService timereportService;
  private Employeecontract contract;
  private Employeeorder workOrder;
  private Employeeorder vacationOrder;
  private Employeeorder sicknessOrder;
  private Employeeorder standbyOrder;
  /** what the database holds, as the persistence context would hand it out — changed in place by an update */
  private final List<Timereport> stored = new ArrayList<>();
  private long nextId = 10;

  @BeforeEach
  void setUp() {
    timereportService = new TimereportService(eventPublisher, employeecontractDAO, referencedayRepository,
        employeeorderDAO, timereportDAO, timereportRepository, publicholidayDAO, workingdayDAO, authorizedUser,
        mock(TimereportAuthorization.class));

    contract = new Employeecontract();
    ReflectionTestUtils.setField(contract, "id", EMPLOYEE_CONTRACT_ID);
    contract.setEmployee(EmployeeTestUtils.createEmployee(OWNER));
    contract.setValidFrom(LocalDate.of(YEAR - 1, 1, 1));
    contract.setDailyWorkingTime(DAILY_WORKING_TIME);
    when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);

    workOrder = employeeorder(WORK_ORDER_ID, OrderType.STANDARD);
    vacationOrder = employeeorder(VACATION_ORDER_ID, OrderType.KRANK_URLAUB_ABWESEND);
    sicknessOrder = employeeorder(SICKNESS_ORDER_ID, OrderType.KRANK_URLAUB_ABWESEND);
    standbyOrder = employeeorder(STANDBY_ORDER_ID, OrderType.BEREITSCHAFT);

    when(referencedayRepository.findByRefdate(any())).thenAnswer(call -> Optional.of(referenceday(call.getArgument(0))));
    when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(any(), anyLong())).thenAnswer(call -> {
      var workingday = new Workingday();
      workingday.setRefday(call.getArgument(0));
      workingday.setStartTime(LocalTime.of(8, 0));
      return workingday;
    });
    when(timereportRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    when(timereportRepository.findAllByEmployeecontractIdAndReferencedayRefdate(anyLong(), any())).thenAnswer(call ->
        stored.stream()
            .filter(timereport -> timereport.getEmployeecontract().getId() == (long) call.getArgument(0))
            .filter(timereport -> timereport.getReferenceday().getRefdate().equals(call.getArgument(1)))
            .toList());
    when(authorizedUser.isManager()).thenReturn(true);
  }

  // --- creating

  @Test
  void an_absence_of_exactly_the_daily_working_time_is_saved() {
    create(vacationOrder, MONDAY, Duration.ofHours(8));

    verify(timereportRepository).save(any());
  }

  @Test
  void an_absence_one_minute_beyond_the_daily_working_time_is_refused() {
    var refusal = refusalOf(() -> create(vacationOrder, MONDAY, Duration.ofHours(8).plusMinutes(1)));

    assertThat(refusal.getErrorCode()).isEqualTo(TR_ABSENCE_DAY_EXCEEDS_TARGET);
    assertThat(refusal.getArguments()).containsExactly(format(MONDAY), OWNER, "8:00", "8:00");
    verify(timereportRepository, never()).save(any());
  }

  @Test
  void working_time_next_to_an_absence_up_to_the_daily_working_time_is_saved() {
    given(vacationOrder, MONDAY, Duration.ofHours(4));

    create(workOrder, MONDAY, Duration.ofHours(4));

    verify(timereportRepository).save(any());
  }

  @Test
  void working_time_next_to_an_absence_one_minute_beyond_is_refused_naming_what_is_left() {
    given(vacationOrder, MONDAY, Duration.ofHours(4));

    var refusal = refusalOf(() -> create(workOrder, MONDAY, Duration.ofHours(4).plusMinutes(1)));

    assertThat(refusal.getErrorCode()).isEqualTo(TR_ABSENCE_DAY_EXCEEDS_TARGET);
    assertThat(refusal.getArguments()).containsExactly(format(MONDAY), OWNER, "8:00", "4:00");
  }

  @Test
  void an_absence_on_a_day_with_working_time_is_saved_up_to_the_daily_working_time() {
    given(workOrder, MONDAY, Duration.ofHours(6));

    create(sicknessOrder, MONDAY, Duration.ofHours(2));

    verify(timereportRepository).save(any());
  }

  @Test
  void an_absence_on_a_day_with_working_time_one_minute_beyond_is_refused() {
    given(workOrder, MONDAY, Duration.ofHours(6));

    var refusal = refusalOf(() -> create(sicknessOrder, MONDAY, Duration.ofHours(2).plusMinutes(1)));

    assertThat(refusal.getErrorCode()).isEqualTo(TR_ABSENCE_DAY_EXCEEDS_TARGET);
    assertThat(refusal.getArguments()).containsExactly(format(MONDAY), OWNER, "8:00", "2:00");
  }

  @Test
  void a_day_without_an_absence_is_not_limited_by_this_rule() {
    given(workOrder, MONDAY, Duration.ofHours(8));

    create(workOrder, MONDAY, Duration.ofHours(4));

    verify(timereportRepository).save(any());
  }

  /** Standby is no working time (#463): it neither fills the day nor is it limited by an absence. */
  @Test
  void standby_counts_neither_way() {
    given(vacationOrder, MONDAY, Duration.ofHours(8));

    create(standbyOrder, MONDAY, Duration.ofHours(4));

    verify(timereportRepository).save(any());
  }

  @Test
  void serial_days_are_checked_each_on_its_own_and_the_refusal_names_the_day() {
    given(workOrder, TUESDAY, Duration.ofMinutes(30));

    var refusal = refusalOf(() -> timereportService.createTimereports(EMPLOYEE_CONTRACT_ID, VACATION_ORDER_ID, MONDAY,
        "", false, DAILY_WORKING_TIME, 3));

    assertThat(refusal.getErrorCode()).isEqualTo(TR_ABSENCE_DAY_EXCEEDS_TARGET);
    assertThat(refusal.getArguments()).containsExactly(format(TUESDAY), OWNER, "8:00", "7:30");
    verify(timereportRepository, never()).save(any());
  }

  // --- days without a target

  @Test
  void an_absence_on_a_weekend_is_refused() {
    var refusal = refusalOf(() -> create(vacationOrder, SATURDAY, Duration.ofHours(1)));

    assertThat(refusal.getErrorCode()).isEqualTo(TR_ABSENCE_DAY_WITHOUT_TARGET);
    assertThat(refusal.getArguments()).containsExactly(format(SATURDAY), OWNER);
  }

  @Test
  void an_absence_on_a_public_holiday_is_refused() {
    when(publicholidayDAO.getPublicHolidaysBetween(eq(WEDNESDAY), eq(WEDNESDAY)))
        .thenReturn(List.of(new Publicholiday(WEDNESDAY, "Feiertag")));

    var refusal = refusalOf(() -> create(vacationOrder, WEDNESDAY, Duration.ofHours(4)));

    assertThat(refusal.getErrorCode()).isEqualTo(TR_ABSENCE_DAY_WITHOUT_TARGET);
  }

  @Test
  void working_on_a_weekend_without_an_absence_stays_possible() {
    create(workOrder, SATURDAY, Duration.ofHours(4));

    verify(timereportRepository).save(any());
  }

  // --- changing a booking

  @Test
  void a_booking_next_to_an_absence_may_grow_up_to_the_daily_working_time() {
    given(vacationOrder, MONDAY, Duration.ofHours(4));
    var work = given(workOrder, MONDAY, Duration.ofHours(2));

    update(work, workOrder, MONDAY, Duration.ofHours(4));

    assertThat(work.getDuration()).isEqualTo(Duration.ofHours(4));
  }

  /** The booking being changed counts with its new duration, not on top of its old one. */
  @Test
  void a_booking_next_to_an_absence_growing_one_minute_beyond_is_refused() {
    given(vacationOrder, MONDAY, Duration.ofHours(4));
    var work = given(workOrder, MONDAY, Duration.ofHours(2));

    var refusal = refusalOf(() -> update(work, workOrder, MONDAY, Duration.ofHours(4).plusMinutes(1)));

    assertThat(refusal.getErrorCode()).isEqualTo(TR_ABSENCE_DAY_EXCEEDS_TARGET);
    assertThat(refusal.getArguments()).containsExactly(format(MONDAY), OWNER, "8:00", "4:00");
  }

  @Test
  void an_absence_itself_can_be_changed_up_to_the_daily_working_time() {
    var vacation = given(vacationOrder, MONDAY, Duration.ofHours(4));

    update(vacation, vacationOrder, MONDAY, DAILY_WORKING_TIME);
    var refusal = refusalOf(() -> update(vacation, vacationOrder, MONDAY, DAILY_WORKING_TIME.plusMinutes(1)));

    assertThat(refusal.getErrorCode()).isEqualTo(TR_ABSENCE_DAY_EXCEEDS_TARGET);
  }

  // --- days that broke the rule before it existed

  @Test
  void on_a_day_beyond_the_limit_from_before_a_booking_keeping_its_duration_can_be_saved() {
    given(vacationOrder, MONDAY, DAILY_WORKING_TIME);
    var work = given(workOrder, MONDAY, Duration.ofHours(2));

    update(work, workOrder, MONDAY, Duration.ofHours(2));

    verify(timereportRepository).save(work);
  }

  @Test
  void on_a_day_beyond_the_limit_from_before_a_booking_can_be_shortened() {
    given(vacationOrder, MONDAY, DAILY_WORKING_TIME);
    var work = given(workOrder, MONDAY, Duration.ofHours(2));

    update(work, workOrder, MONDAY, Duration.ofHours(1));

    assertThat(work.getDuration()).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void on_a_day_beyond_the_limit_from_before_a_booking_cannot_grow() {
    given(vacationOrder, MONDAY, DAILY_WORKING_TIME);
    var work = given(workOrder, MONDAY, Duration.ofHours(2));

    var refusal = refusalOf(() -> update(work, workOrder, MONDAY, Duration.ofHours(2).plusMinutes(1)));

    assertThat(refusal.getErrorCode()).isEqualTo(TR_ABSENCE_DAY_EXCEEDS_TARGET);
    assertThat(refusal.getArguments()).containsExactly(format(MONDAY), OWNER, "8:00", "0:00");
  }

  @Test
  void on_a_day_beyond_the_limit_from_before_nothing_can_be_added() {
    given(vacationOrder, MONDAY, DAILY_WORKING_TIME);
    given(workOrder, MONDAY, Duration.ofHours(2));

    var refusal = refusalOf(() -> create(workOrder, MONDAY, Duration.ofMinutes(1)));

    assertThat(refusal.getErrorCode()).isEqualTo(TR_ABSENCE_DAY_EXCEEDS_TARGET);
  }

  // --- moving to another day and to another suborder

  @Test
  void shifting_a_booking_onto_a_day_with_an_absence_is_refused_beyond_the_limit() {
    given(vacationOrder, TUESDAY, Duration.ofHours(7));
    var work = given(workOrder, MONDAY, Duration.ofHours(2));

    var refusal = refusalOf(() -> timereportService.shiftDays(work.getId(), 1));

    assertThat(refusal.getErrorCode()).isEqualTo(TR_ABSENCE_DAY_EXCEEDS_TARGET);
    assertThat(refusal.getArguments()).containsExactly(format(TUESDAY), OWNER, "8:00", "1:00");
  }

  @Test
  void shifting_a_booking_onto_a_day_with_an_absence_is_saved_up_to_the_limit() {
    given(vacationOrder, TUESDAY, Duration.ofHours(6));
    var work = given(workOrder, MONDAY, Duration.ofHours(2));

    timereportService.shiftDays(work.getId(), 1);

    assertThat(work.getReferenceday().getRefdate()).isEqualTo(TUESDAY);
  }

  /** Taking a booking off a day beyond the limit from before only lowers the excess there. */
  @Test
  void shifting_a_booking_away_from_a_day_beyond_the_limit_is_saved() {
    given(vacationOrder, MONDAY, DAILY_WORKING_TIME);
    var work = given(workOrder, MONDAY, Duration.ofHours(2));

    timereportService.shiftDays(work.getId(), 1);

    assertThat(work.getReferenceday().getRefdate()).isEqualTo(TUESDAY);
  }

  /**
   * Moving bookings to another suborder (#1426) goes through the forced update. Moving working time to an
   * absence turns a day without a limit into one with it.
   */
  @Test
  void moving_working_time_to_an_absence_on_a_day_beyond_the_daily_working_time_is_refused() {
    given(workOrder, MONDAY, Duration.ofHours(6));
    var work = given(workOrder, MONDAY, Duration.ofHours(3));

    var refusal = refusalOf(() -> timereportService.updateTimereport(work.getId(), EMPLOYEE_CONTRACT_ID, VACATION_ORDER_ID,
        MONDAY, "", false, work.getDuration(), true));

    assertThat(refusal.getErrorCode()).isEqualTo(TR_ABSENCE_DAY_EXCEEDS_TARGET);
  }

  @Test
  void moving_working_time_to_an_absence_within_the_daily_working_time_is_saved() {
    given(workOrder, MONDAY, Duration.ofHours(5));
    var work = given(workOrder, MONDAY, Duration.ofHours(3));

    timereportService.updateTimereport(work.getId(), EMPLOYEE_CONTRACT_ID, VACATION_ORDER_ID,
        MONDAY, "", false, work.getDuration(), true);

    assertThat(work.getEmployeeorder()).isSameAs(vacationOrder);
  }

  // --- helpers

  private void create(Employeeorder order, LocalDate day, Duration duration) {
    timereportService.createTimereports(EMPLOYEE_CONTRACT_ID, order.getId(), day, "", false, duration, 1);
  }

  private void update(Timereport timereport, Employeeorder order, LocalDate day, Duration duration) {
    timereportService.updateTimereport(timereport.getId(), EMPLOYEE_CONTRACT_ID, order.getId(), day, "", false, duration);
  }

  private Timereport given(Employeeorder order, LocalDate day, Duration duration) {
    var timereport = new Timereport();
    ReflectionTestUtils.setField(timereport, "id", nextId++);
    timereport.setEmployeeorder(order);
    timereport.setReferenceday(referenceday(day));
    timereport.setTaskdescription("");
    timereport.setDuration(duration);
    timereport.setStatus(TIMEREPORT_STATUS_OPEN);
    stored.add(timereport);
    when(timereportRepository.findById(timereport.getId())).thenReturn(Optional.of(timereport));
    return timereport;
  }

  /** The message of the refusal, {@code null} where the booking was saved. */
  private static ServiceFeedbackMessage refusalOf(Runnable action) {
    var refusal = catchThrowableOfType(BusinessRuleException.class, action::run);
    return refusal == null ? null : refusal.getMessages().getFirst();
  }

  private Employeeorder employeeorder(long id, OrderType orderType) {
    var customerorder = new Customerorder();
    customerorder.setOrderType(orderType);
    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    var employeeorder = new Employeeorder();
    ReflectionTestUtils.setField(employeeorder, "id", id);
    employeeorder.setEmployeecontract(contract);
    employeeorder.setSuborder(suborder);
    employeeorder.setFromDate(contract.getValidFrom());
    when(employeeorderDAO.getEmployeeorderById(id)).thenReturn(employeeorder);
    return employeeorder;
  }

  private static Referenceday referenceday(LocalDate date) {
    var referenceday = new Referenceday();
    referenceday.setRefdate(date);
    return referenceday;
  }

  /* The limit is about the time worked and taken off; a time surcharge is neither (#1469): 4 hours of night
     work with a credit of 1 hour leave 4 hours for the absence. */
  @Test
  void a_time_surcharge_does_not_count_towards_the_limit() {
    given(workOrder, MONDAY, Duration.ofHours(4)).setSurcharge(SurchargeKind.NIGHT, 25);

    create(sicknessOrder, MONDAY, Duration.ofHours(4));

    verify(timereportRepository).save(any());
  }
}
