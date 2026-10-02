package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;
import static de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType.WORKED;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.persistence.WorkingdayRepository;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
import de.hbt.salat.dailyreport.preferences.DailyPreferences;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.OrderType;

/**
 * Up to when a day is booked (#1263): the booking form adds the duration being entered and shows
 * where that booking ends. The value has to be the quitting time the daily view shows after saving,
 * so standby counts as little as it does there (#463).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class WorkingdayBookedUntilTest {

  private static final long EMPLOYEE_CONTRACT_ID = 42L;
  private static final LocalDate DAY = LocalDate.of(2026, 3, 17);

  @InjectMocks
  private WorkingdayService workingdayService;

  @Mock
  private WorkingdayRepository workingdayRepository;
  @Mock
  private TimereportDAO timereportDAO;
  @Mock
  private AuthorizedUser authorizedUser;
  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private DailyPreferenceService dailyPreferenceService;

  @BeforeEach
  void setUp() {
    var salatUser = new SalatUser();
    salatUser.setLoginname("w11");
    var employee = new Employee();
    employee.setSign("w11");
    employee.setSalatUser(salatUser);
    var contract = new Employeecontract();
    setField(contract, "id", EMPLOYEE_CONTRACT_ID);
    contract.setEmployee(employee);

    when(authorizedUser.getEffectiveLoginSign()).thenReturn("w11");
    when(employeecontractService.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
    when(workingdayRepository.findByRefdayAndEmployeecontractId(DAY, EMPLOYEE_CONTRACT_ID)).thenReturn(Optional.empty());
    when(timereportDAO.getTimereportsByDateAndEmployeeContractId(EMPLOYEE_CONTRACT_ID, DAY)).thenReturn(List.of());
    when(dailyPreferenceService.getForEmployeeContractId(anyLong()))
        .thenReturn(new DailyPreferences(LocalTime.of(9, 0), false));
  }

  @Test
  void without_working_day_the_day_starts_at_the_preferred_start() {
    assertThat(workingdayService.getBookedUntilMinutes(EMPLOYEE_CONTRACT_ID, DAY, null)).hasValue(9 * 60);
  }

  @Test
  void start_break_and_bookings_add_up() {
    storeWorkingday(WORKED, 7, 0, 0, 30);
    bookings(booking(1L, OrderType.STANDARD, 3, 0), booking(2L, OrderType.STANDARD, 1, 15));

    assertThat(workingdayService.getBookedUntilMinutes(EMPLOYEE_CONTRACT_ID, DAY, null))
        .hasValue(7 * 60 + 30 + 4 * 60 + 15);
  }

  @Test
  void standby_does_not_move_the_end_of_the_day() {
    storeWorkingday(WORKED, 7, 0, 0, 0);
    bookings(booking(1L, OrderType.STANDARD, 3, 0), booking(2L, OrderType.BEREITSCHAFT, 8, 0));

    assertThat(workingdayService.getBookedUntilMinutes(EMPLOYEE_CONTRACT_ID, DAY, null)).hasValue(10 * 60);
  }

  /** The edited booking counts with the duration in the form, which the form adds itself. */
  @Test
  void the_edited_booking_is_left_out() {
    storeWorkingday(WORKED, 7, 0, 0, 0);
    bookings(booking(1L, OrderType.STANDARD, 3, 0), booking(2L, OrderType.STANDARD, 1, 15));

    assertThat(workingdayService.getBookedUntilMinutes(EMPLOYEE_CONTRACT_ID, DAY, 2L)).hasValue(10 * 60);
  }

  /** Not wrapped at midnight: the form decides that there is no time of day to show. */
  @Test
  void a_day_booked_past_midnight_is_not_wrapped() {
    storeWorkingday(WORKED, 20, 0, 0, 0);
    bookings(booking(1L, OrderType.STANDARD, 5, 0));

    assertThat(workingdayService.getBookedUntilMinutes(EMPLOYEE_CONTRACT_ID, DAY, null)).hasValue(25 * 60);
  }

  @Test
  void a_day_marked_not_worked_has_no_starting_point() {
    storeWorkingday(NOT_WORKED, 0, 0, 0, 0);

    assertThat(workingdayService.getBookedUntilMinutes(EMPLOYEE_CONTRACT_ID, DAY, null)).isEmpty();
  }

  private void storeWorkingday(Workingday.WorkingDayType type, int startHour, int startMinute,
      int breakHours, int breakMinutes) {
    var workingday = new Workingday();
    workingday.setRefday(DAY);
    workingday.setType(type);
    workingday.setStarttimehour(startHour);
    workingday.setStarttimeminute(startMinute);
    workingday.setBreakhours(breakHours);
    workingday.setBreakminutes(breakMinutes);
    when(workingdayRepository.findByRefdayAndEmployeecontractId(DAY, EMPLOYEE_CONTRACT_ID)).thenReturn(Optional.of(workingday));
  }

  private void bookings(TimereportDTO... bookings) {
    when(timereportDAO.getTimereportsByDateAndEmployeeContractId(EMPLOYEE_CONTRACT_ID, DAY)).thenReturn(List.of(bookings));
  }

  private static TimereportDTO booking(long id, OrderType orderType, int hours, int minutes) {
    return TimereportDTO.builder()
        .id(id)
        .orderType(orderType)
        .duration(Duration.ofHours(hours).plusMinutes(minutes))
        .build();
  }
}
