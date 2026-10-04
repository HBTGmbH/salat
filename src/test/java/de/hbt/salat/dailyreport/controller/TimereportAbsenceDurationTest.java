package de.hbt.salat.dailyreport.controller;

import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
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
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.EmployeeorderService;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.order.service.SuborderSummary;

/**
 * Was das Buchungsformular braucht, um eine Abwesenheit mit dem Rest des Tagessolls vorzubelegen
 * (#1214): welche Unteraufträge zu einer Abwesenheit gehören, und die Restzeit des Tages im Formular.
 * Die Restzeit bekommt nur eine neue Buchung — beim Bearbeiten ändert ein Auftragswechsel die Dauer
 * nicht. Wann das Feld vorbelegt wird, entscheidet das Formular selbst ({@code
 * AbsenceDurationPrefillE2ETest}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportAbsenceDurationTest {

  private static final long CONTRACT_ID = 7L;
  private static final long LOGIN_EMPLOYEE_ID = 8L;
  private static final long TIMEREPORT_ID = 3L;
  private static final long SICK_ORDER_ID = 20L;
  private static final long PROJECT_ORDER_ID = 21L;
  private static final long STANDBY_ORDER_ID = 22L;
  private static final long SICK_SUBORDER_ID = 30L;
  private static final long PROJECT_SUBORDER_ID = 31L;
  private static final long STANDBY_SUBORDER_ID = 32L;
  /** Bewusst nicht heute: sonst liest das Befüllen des Modells zusätzlich den Arbeitstag. */
  private static final LocalDate DATE = LocalDate.of(2026, 3, 2);
  private static final LocalDate OTHER_DATE = LocalDate.of(2026, 3, 3);

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
    when(timereportService.getEmployeecontractIdForUpdate(eq(TIMEREPORT_ID), any())).thenReturn(CONTRACT_ID);
    when(timereportPreferenceService.getForCurrentUser())
        .thenReturn(new TimereportPreferences(null, DurationInputMode.DURATION, DurationInputMode.DURATION));

    var sick = customerorder(SICK_ORDER_ID, "KRANK", OrderType.KRANK_URLAUB_ABWESEND);
    var project = customerorder(PROJECT_ORDER_ID, "ALPHA", OrderType.STANDARD);
    var standby = customerorder(STANDBY_ORDER_ID, "RUF", OrderType.BEREITSCHAFT);
    when(customerorderService.getCustomerordersWithValidEmployeeOrders(eq(CONTRACT_ID), any()))
        .thenReturn(List.of(sick, project, standby));
    when(suborderService.getSuborderSummaries(eq(CONTRACT_ID), eq(SICK_ORDER_ID), any()))
        .thenReturn(List.of(new SuborderSummary(SICK_SUBORDER_ID, "KRANK/Krankheit", "Krankheit", false, false)));
    when(suborderService.getSuborderSummaries(eq(CONTRACT_ID), eq(PROJECT_ORDER_ID), any()))
        .thenReturn(List.of(new SuborderSummary(PROJECT_SUBORDER_ID, "ALPHA-DEV", "Entwicklung", false, false)));
    when(suborderService.getSuborderSummaries(eq(CONTRACT_ID), eq(STANDBY_ORDER_ID), any()))
        .thenReturn(List.of(new SuborderSummary(STANDBY_SUBORDER_ID, "RUF-01", "Rufbereitschaft", false, false)));

    when(dailyService.getRemainingDayTarget(DATE, CONTRACT_ID)).thenReturn(Duration.ofMinutes(5 * 60 + 15));
    when(dailyService.getRemainingDayTarget(OTHER_DATE, CONTRACT_ID)).thenReturn(Duration.ofHours(8));
  }

  @Test
  void only_the_suborders_of_an_absence_order_are_marked_as_absence() {
    var options = SuborderOption.bookable(customerorderService, suborderService, CONTRACT_ID, DATE);

    assertThat(options).extracting(SuborderOption::id, SuborderOption::absence).containsExactly(
        tuple(SICK_SUBORDER_ID, true),
        tuple(PROJECT_SUBORDER_ID, false),
        tuple(STANDBY_SUBORDER_ID, false));
  }

  @Test
  void the_create_form_carries_the_rest_of_the_day_in_minutes() {
    var model = new ExtendedModelMap();

    controller.createForm(null, null, DATE, SICK_SUBORDER_ID, null, null, null, null, null, null, model);

    assertThat(model.get("absenceDurationMinutes")).isEqualTo(5 * 60 + 15L);
  }

  /** Gewählt ist ein Unterauftrag aus der Adresse (Deeplink, Befehlspalette) oder der Favorit. */
  @Test
  void a_suborder_from_the_address_counts_as_chosen() {
    var model = new ExtendedModelMap();

    controller.createForm(null, null, DATE, SICK_SUBORDER_ID, null, null, null, null, null, null, model);

    assertThat(model.get("suborderPreselectedByDefault")).isEqualTo(false);
  }

  @Test
  void the_favourite_counts_as_chosen() {
    when(timereportPreferenceService.getForCurrentUser())
        .thenReturn(new TimereportPreferences(SICK_SUBORDER_ID, DurationInputMode.DURATION, DurationInputMode.DURATION));
    var model = new ExtendedModelMap();

    controller.createForm(null, null, DATE, null, null, null, null, null, null, null, model);

    assertThat(model.get("suborderPreselectedByDefault")).isEqualTo(false);
  }

  /** Ohne beides steht der erste der Liste vorgewählt — hier die Abwesenheit, gewählt hat sie niemand. */
  @Test
  void the_first_of_the_list_is_only_preselected_by_default() {
    var model = new ExtendedModelMap();

    controller.createForm(null, null, DATE, null, null, null, null, null, null, null, model);

    assertThat(((TimereportForm) model.get("timereportForm")).getSuborderId()).isEqualTo(SICK_SUBORDER_ID);
    assertThat(model.get("suborderPreselectedByDefault")).isEqualTo(true);
  }

  @Test
  void a_new_date_brings_the_rest_of_that_day() {
    var form = newBooking();
    form.setReferenceday(OTHER_DATE);
    var model = new ExtendedModelMap();

    controller.refreshOrders(null, form, model);

    assertThat(model.get("absenceDurationMinutes")).isEqualTo(8 * 60L);
  }

  @Test
  void a_day_without_rest_carries_zero() {
    when(dailyService.getRemainingDayTarget(DATE, CONTRACT_ID)).thenReturn(Duration.ZERO);
    var model = new ExtendedModelMap();

    controller.createForm(null, null, DATE, SICK_SUBORDER_ID, null, null, null, null, null, null, model);

    assertThat(model.get("absenceDurationMinutes")).isEqualTo(0L);
  }

  @Test
  void the_edit_form_carries_no_rest() {
    var booking = mock(TimereportDTO.class);
    when(booking.getEmployeecontractId()).thenReturn(CONTRACT_ID);
    when(booking.getReferenceday()).thenReturn(DATE);
    when(booking.getSuborderId()).thenReturn(SICK_SUBORDER_ID);
    when(timereportService.getTimereportById(TIMEREPORT_ID)).thenReturn(booking);
    var model = new ExtendedModelMap();

    controller.editForm(TIMEREPORT_ID, null, null, model);

    assertThat(model).doesNotContainKey("absenceDurationMinutes");
    verify(dailyService, never()).getRemainingDayTarget(any(), anyLong());
  }

  @Test
  void a_new_date_in_the_edit_form_carries_no_rest() {
    var form = newBooking();
    form.setId(TIMEREPORT_ID);
    var model = new ExtendedModelMap();

    controller.refreshOrders(null, form, model);

    assertThat(model).doesNotContainKey("absenceDurationMinutes");
    verify(dailyService, never()).getRemainingDayTarget(any(), anyLong());
  }

  private static TimereportForm newBooking() {
    var form = new TimereportForm();
    form.setReferenceday(DATE);
    form.setSuborderId(SICK_SUBORDER_ID);
    return form;
  }

  private static Customerorder customerorder(long id, String sign, OrderType orderType) {
    var customer = new Customer();
    customer.setShortname(uniqueShortname("HBT"));
    var order = mock(Customerorder.class);
    when(order.getId()).thenReturn(id);
    when(order.getSign()).thenReturn(sign);
    when(order.getShortdescription()).thenReturn(sign);
    when(order.getCustomer()).thenReturn(customer);
    when(order.getOrderType()).thenReturn(orderType);
    return order;
  }

  private static Employeecontract contract() {
    var employee = mock(Employee.class);
    when(employee.getName()).thenReturn("Olga Eigen");
    when(employee.getSign()).thenReturn("oei");
    var contract = mock(Employeecontract.class);
    when(contract.getId()).thenReturn(CONTRACT_ID);
    when(contract.getEmployee()).thenReturn(employee);
    when(contract.getTimeString()).thenReturn("01.01.2026 - ");
    when(contract.getOpenEnd()).thenReturn(true);
    return contract;
  }
}
