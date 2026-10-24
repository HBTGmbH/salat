package de.hbt.salat.dailyreport.controller;

import static de.hbt.salat.order.domain.SurchargeKind.HOLIDAY;
import static de.hbt.salat.order.domain.SurchargeKind.NIGHT;
import static de.hbt.salat.order.domain.SurchargeKind.SATURDAY;
import static de.hbt.salat.order.domain.SurchargeKind.SUNDAY;
import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.ui.ExtendedModelMap;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
import de.hbt.salat.dailyreport.preferences.DurationInputMode;
import de.hbt.salat.dailyreport.preferences.TimereportPreferenceService;
import de.hbt.salat.dailyreport.preferences.TimereportPreferences;
import de.hbt.salat.dailyreport.service.DailyService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.favorites.service.FavoriteService;
import de.hbt.salat.notification.service.NotificationService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.SurchargeRate;
import de.hbt.salat.order.domain.TicketReferencePolicy;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.EmployeeorderService;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.order.service.SuborderSummary;

/**
 * The time surcharge choice of the booking form (#1469): only what may be chosen - the kinds the suborder offers,
 * Saturday, Sunday and holiday on their day -, the kind of the day preselected for a new booking, and for an
 * edited booking on its own day the kind it was stored with, at its stored rate.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportSurchargeFormTest {

  private static final long CONTRACT_ID = 7L;
  private static final long LOGIN_EMPLOYEE_ID = 8L;
  private static final long TIMEREPORT_ID = 3L;
  private static final long ORDER_ID = 20L;
  private static final long SUBORDER_ID = 30L;
  /** Saturday and Sunday of a past week, not today: the form reads the working day of today besides. */
  private static final LocalDate SATURDAY_DATE = LocalDate.of(2026, 3, 7);
  private static final LocalDate WEDNESDAY_DATE = LocalDate.of(2026, 3, 4);

  @Mock private TimereportService timereportService;
  @Mock private EmployeecontractService employeecontractService;
  @Mock private CustomerorderService customerorderService;
  @Mock private SuborderService suborderService;
  @Mock private EmployeeorderService employeeorderService;
  @Mock private WorkingdayService workingdayService;
  @Mock private DailyService dailyService;
  @Mock private FavoriteService favoriteService;
  @Mock private EmployeeService employeeService;
  @Mock private MessageSourceAccessor messages;
  @Mock private ErrorCodeViewHelper errorCodeViewHelper;
  @Mock private DailyPreferenceService dailyPreferenceService;
  @Mock private TimereportPreferenceService timereportPreferenceService;
  @Mock private NotificationService notificationService;
  @Mock private AuthorizedUser authorizedUser;
  @Mock private AuthorizedEmployee authorizedEmployee;

  @InjectMocks private TimereportController controller;

  @BeforeEach
  void setUp() {
    var contract = contract();
    when(employeecontractService.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract);
    var loginEmployee = mock(Employee.class);
    when(loginEmployee.getId()).thenReturn(LOGIN_EMPLOYEE_ID);
    when(employeeService.getLoginEmployee()).thenReturn(loginEmployee);
    when(employeecontractService.getCurrentContract(LOGIN_EMPLOYEE_ID)).thenReturn(Optional.of(contract));
    when(timereportPreferenceService.getForCurrentUser())
        .thenReturn(new TimereportPreferences(null, DurationInputMode.DURATION, DurationInputMode.DURATION, false));
    when(dailyService.getRemainingDayTarget(any(), eq(CONTRACT_ID))).thenReturn(Duration.ZERO);

    var order = customerorder();
    when(customerorderService.getCustomerordersWithValidEmployeeOrders(eq(CONTRACT_ID), any())).thenReturn(List.of(order));
    when(suborderService.getSuborderSummaries(eq(CONTRACT_ID), eq(ORDER_ID), any()))
        .thenReturn(List.of(new SuborderSummary(SUBORDER_ID, "ALPHA-DEV", "Entwicklung", false, false,
            TicketReferencePolicy.DEFAULT, Map.of(NIGHT, SurchargeRate.of(25), SATURDAY, SurchargeRate.of(50)))));

    when(timereportService.getSurchargeDayKinds(SATURDAY_DATE)).thenReturn(List.of(SATURDAY));
    when(timereportService.getSurchargeDayKinds(WEDNESDAY_DATE)).thenReturn(List.of());
    when(timereportService.getPresetSurchargeKind(SATURDAY_DATE, Set.of(NIGHT, SATURDAY))).thenReturn(SATURDAY);
  }

  @Test
  void a_new_booking_on_a_saturday_offers_night_and_saturday_and_preselects_the_saturday() {
    var model = new ExtendedModelMap();

    controller.createForm(null, null, SATURDAY_DATE, SUBORDER_ID, null, null, null, null, null, null, model);

    assertThat(((TimereportForm) model.get("timereportForm")).getSurchargeKind()).isEqualTo(SATURDAY);
    assertThat(options(model)).extracting(SurchargeOption::kind, SurchargeOption::percent, SurchargeOption::selectable)
        .containsExactly(
            tuple(NIGHT, 25, true),
            tuple(SATURDAY, 50, true),
            tuple(SUNDAY, null, false),
            tuple(HOLIDAY, null, false));
    assertThat(model.get("surchargeSelectable")).isEqualTo(true);
  }

  @Test
  void a_saturday_rate_is_no_choice_on_a_weekday() {
    var model = new ExtendedModelMap();

    controller.createForm(null, null, WEDNESDAY_DATE, SUBORDER_ID, null, null, null, null, null, null, model);

    assertThat(((TimereportForm) model.get("timereportForm")).getSurchargeKind()).isNull();
    assertThat(options(model)).filteredOn(SurchargeOption::selectable).extracting(SurchargeOption::kind)
        .containsExactly(NIGHT);
  }

  /* A holiday taken off the calendar since: on its own day the booking keeps its kind at its stored rate. */
  @Test
  void an_edited_booking_keeps_its_kind_and_rate_on_its_own_day() {
    var booking = mock(TimereportDTO.class);
    when(booking.getEmployeecontractId()).thenReturn(CONTRACT_ID);
    when(booking.getReferenceday()).thenReturn(WEDNESDAY_DATE);
    when(booking.getSuborderId()).thenReturn(SUBORDER_ID);
    when(booking.getDuration()).thenReturn(Duration.ofHours(2));
    when(booking.getTicketReferences()).thenReturn(List.of());
    when(booking.getSurchargeKind()).thenReturn(HOLIDAY);
    when(booking.getSurchargePercent()).thenReturn(100);
    when(timereportService.getTimereportById(TIMEREPORT_ID)).thenReturn(booking);
    var model = new ExtendedModelMap();

    controller.editForm(TIMEREPORT_ID, null, null, model);

    assertThat(options(model)).filteredOn(SurchargeOption::selectable)
        .extracting(SurchargeOption::kind, SurchargeOption::percent)
        .containsExactly(tuple(NIGHT, 25), tuple(HOLIDAY, 100));
    assertThat(model.get("storedSurchargeKind")).isEqualTo("HOLIDAY");
  }

  @SuppressWarnings("unchecked")
  private static List<SurchargeOption> options(ExtendedModelMap model) {
    return (List<SurchargeOption>) model.get("surchargeOptions");
  }

  private static Customerorder customerorder() {
    var customer = new Customer();
    customer.setShortname(uniqueShortname("HBT"));
    var order = mock(Customerorder.class);
    when(order.getId()).thenReturn(ORDER_ID);
    when(order.getSign()).thenReturn("ALPHA");
    when(order.getShortdescription()).thenReturn("ALPHA");
    when(order.getCustomer()).thenReturn(customer);
    when(order.getOrderType()).thenReturn(OrderType.STANDARD);
    return order;
  }

  private static Employeecontract contract() {
    var employee = mock(Employee.class);
    when(employee.getName()).thenReturn("Person A");
    when(employee.getSign()).thenReturn("pa");
    var contract = mock(Employeecontract.class);
    when(contract.getId()).thenReturn(CONTRACT_ID);
    when(contract.getEmployee()).thenReturn(employee);
    when(contract.getTimeString()).thenReturn("01.01.2026 - ");
    when(contract.getOpenEnd()).thenReturn(true);
    return contract;
  }
}
