package de.hbt.salat.dailyreport.controller;

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
import de.hbt.salat.order.domain.TicketReferencePolicy;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.EmployeeorderService;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.order.service.SuborderSummary;

/**
 * Das Modell des Buchungsformulars, wenn der Unterauftrag in zwei Schritten gewählt wird (#1522): welche
 * Aufträge die Auftragsauswahl anbietet, welcher vorgewählt ist und was ein Datumswechsel wählt. Das
 * Filtern der Unteraufträge im Browser prüft {@code TwoStepSuborderSelectE2ETest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportTwoStepSuborderTest {

  private static final long CONTRACT_ID = 7L;
  private static final long LOGIN_EMPLOYEE_ID = 8L;
  private static final long TIMEREPORT_ID = 3L;
  private static final long ALPHA_ID = 20L;
  private static final long BETA_ID = 21L;
  private static final long GAMMA_ID = 22L;
  private static final long ALPHA_1 = 30L;
  private static final long ALPHA_2 = 31L;
  private static final long BETA_1 = 40L;
  private static final long BETA_2 = 41L;
  private static final long GAMMA_1 = 50L;
  /** Bewusst nicht heute: sonst liest das Befüllen des Modells zusätzlich den Arbeitstag. */
  private static final LocalDate DATE = LocalDate.of(2026, 3, 2);
  /** Am Folgetag ist ALPHA-1 nicht mehr buchbar und GAMMA gar nicht. */
  private static final LocalDate NEXT_DATE = LocalDate.of(2026, 3, 3);

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
    when(dailyService.getRemainingDayTarget(any(), eq(CONTRACT_ID))).thenReturn(Duration.ZERO);
    preferences(null, true);

    var alpha = customerorder(ALPHA_ID, "ALPHA", "Wartung", "Kunde A");
    var beta = customerorder(BETA_ID, "BETA", "Neuentwicklung", "Kunde B");
    var gamma = customerorder(GAMMA_ID, "GAMMA", "Betrieb", "Kunde C");
    when(customerorderService.getCustomerordersWithValidEmployeeOrders(CONTRACT_ID, DATE))
        .thenReturn(List.of(alpha, beta, gamma));
    when(customerorderService.getCustomerordersWithValidEmployeeOrders(CONTRACT_ID, NEXT_DATE))
        .thenReturn(List.of(alpha, beta));
    when(suborderService.getSuborderSummaries(CONTRACT_ID, ALPHA_ID, DATE))
        .thenReturn(List.of(suborder(ALPHA_1, "ALPHA/1"), suborder(ALPHA_2, "ALPHA/2")));
    when(suborderService.getSuborderSummaries(CONTRACT_ID, ALPHA_ID, NEXT_DATE))
        .thenReturn(List.of(suborder(ALPHA_2, "ALPHA/2")));
    when(suborderService.getSuborderSummaries(eq(CONTRACT_ID), eq(BETA_ID), any()))
        .thenReturn(List.of(suborder(BETA_1, "BETA/1"), suborder(BETA_2, "BETA/2")));
    when(suborderService.getSuborderSummaries(CONTRACT_ID, GAMMA_ID, DATE))
        .thenReturn(List.of(suborder(GAMMA_1, "GAMMA/1")));
  }

  @Test
  void without_the_setting_the_form_has_no_order_select() {
    preferences(null, false);
    var model = new ExtendedModelMap();

    controller.createForm(null, null, DATE, null, null, null, null, null, null, null, model);

    assertThat(model.get("twoStepSuborderSelect")).isEqualTo(false);
    assertThat(model).doesNotContainKeys("bookableOrders", "selectedOrderId");
  }

  @Test
  void the_order_select_offers_each_order_with_a_bookable_suborder_once_in_the_order_of_the_list() {
    var model = new ExtendedModelMap();

    controller.createForm(null, null, DATE, null, null, null, null, null, null, null, model);

    assertThat(model.get("twoStepSuborderSelect")).isEqualTo(true);
    assertThat(bookableOrders(model))
        .extracting(BookableOrderOption::id, BookableOrderOption::label, BookableOrderOption::subtext)
        .containsExactly(
            tuple(ALPHA_ID, "ALPHA - Wartung", "Kunde A"),
            tuple(BETA_ID, "BETA - Neuentwicklung", "Kunde B"),
            tuple(GAMMA_ID, "GAMMA - Betrieb", "Kunde C"));
  }

  @Test
  void the_suborder_options_name_their_order() {
    var options = SuborderOption.bookable(customerorderService, suborderService, CONTRACT_ID, DATE);

    assertThat(options).extracting(SuborderOption::id, SuborderOption::orderId).containsExactly(
        tuple(ALPHA_1, ALPHA_ID), tuple(ALPHA_2, ALPHA_ID), tuple(BETA_1, BETA_ID), tuple(BETA_2, BETA_ID),
        tuple(GAMMA_1, GAMMA_ID));
  }

  /** Wie heute der erste Unterauftrag: der erste Auftrag und der erste seiner Unteraufträge. */
  @Test
  void without_a_default_the_first_order_and_its_first_suborder_are_preselected() {
    var model = new ExtendedModelMap();

    controller.createForm(null, null, DATE, null, null, null, null, null, null, null, model);

    assertThat(form(model).getSuborderId()).isEqualTo(ALPHA_1);
    assertThat(model.get("selectedOrderId")).isEqualTo(ALPHA_ID);
    assertThat(model.get("suborderPreselectedByDefault")).isEqualTo(true);
  }

  @Test
  void the_favourite_preselects_its_order() {
    preferences(BETA_2, true);
    var model = new ExtendedModelMap();

    controller.createForm(null, null, DATE, null, null, null, null, null, null, null, model);

    assertThat(form(model).getSuborderId()).isEqualTo(BETA_2);
    assertThat(model.get("selectedOrderId")).isEqualTo(BETA_ID);
  }

  /** Deeplink, Befehlspalette, „Mit Kollegen teilen", „Speichern und neu" und Kopieren öffnen so. */
  @Test
  void a_suborder_from_the_address_preselects_its_order() {
    var model = new ExtendedModelMap();

    controller.createForm(null, null, DATE, GAMMA_1, null, null, null, null, null, null, model);

    assertThat(model.get("selectedOrderId")).isEqualTo(GAMMA_ID);
  }

  @Test
  void the_edit_form_preselects_the_order_of_the_booking() {
    var booking = mock(TimereportDTO.class);
    when(booking.getEmployeecontractId()).thenReturn(CONTRACT_ID);
    when(booking.getReferenceday()).thenReturn(DATE);
    when(booking.getSuborderId()).thenReturn(BETA_1);
    when(booking.getCustomerorderId()).thenReturn(BETA_ID);
    when(booking.getDuration()).thenReturn(Duration.ofHours(1));
    when(timereportService.getTimereportById(TIMEREPORT_ID)).thenReturn(booking);
    var model = new ExtendedModelMap();

    controller.editForm(TIMEREPORT_ID, null, null, model);

    assertThat(model.get("selectedOrderId")).isEqualTo(BETA_ID);
  }

  @Test
  void a_new_date_keeps_a_suborder_still_bookable() {
    var form = booking(DATE, BETA_2, null);
    form.setReferenceday(NEXT_DATE);
    var model = new ExtendedModelMap();

    controller.refreshOrders(null, form, model);

    assertThat(form.getSuborderId()).isEqualTo(BETA_2);
    assertThat(model.get("selectedOrderId")).isEqualTo(BETA_ID);
    assertThat(bookableOrders(model)).extracting(BookableOrderOption::id).containsExactly(ALPHA_ID, BETA_ID);
  }

  /** Der Browser nennt den Auftrag, die Auswahl selbst übermittelt nichts. */
  @Test
  void a_new_date_keeps_the_order_and_takes_its_first_suborder_where_only_the_order_is_bookable() {
    var form = booking(NEXT_DATE, ALPHA_1, ALPHA_ID);
    var model = new ExtendedModelMap();

    controller.refreshOrders(null, form, model);

    assertThat(form.getSuborderId()).isEqualTo(ALPHA_2);
    assertThat(model.get("selectedOrderId")).isEqualTo(ALPHA_ID);
  }

  @Test
  void a_new_date_keeps_an_order_whose_suborder_is_not_chosen_yet() {
    var form = booking(NEXT_DATE, null, BETA_ID);
    var model = new ExtendedModelMap();

    controller.refreshOrders(null, form, model);

    assertThat(form.getSuborderId()).isEqualTo(BETA_1);
    assertThat(model.get("selectedOrderId")).isEqualTo(BETA_ID);
  }

  @Test
  void a_new_date_without_the_order_falls_back_to_the_first_of_the_list() {
    var form = booking(NEXT_DATE, GAMMA_1, GAMMA_ID);
    var model = new ExtendedModelMap();

    controller.refreshOrders(null, form, model);

    assertThat(form.getSuborderId()).isEqualTo(ALPHA_2);
    assertThat(model.get("selectedOrderId")).isEqualTo(ALPHA_ID);
  }

  /** Ohne die Einstellung nennt der Browser keinen Auftrag, es bleibt beim ersten der Liste. */
  @Test
  void without_an_order_a_new_date_takes_the_first_of_the_list_as_before() {
    preferences(null, false);
    var form = booking(NEXT_DATE, ALPHA_1, null);
    var model = new ExtendedModelMap();

    controller.refreshOrders(null, form, model);

    assertThat(form.getSuborderId()).isEqualTo(ALPHA_2);
    assertThat(model).doesNotContainKeys("bookableOrders", "selectedOrderId");
  }

  private void preferences(Long favoriteSuborderId, boolean twoStep) {
    when(timereportPreferenceService.getForCurrentUser()).thenReturn(new TimereportPreferences(
        favoriteSuborderId, DurationInputMode.DURATION, DurationInputMode.DURATION, twoStep));
  }

  @SuppressWarnings("unchecked")
  private static List<BookableOrderOption> bookableOrders(ExtendedModelMap model) {
    return (List<BookableOrderOption>) model.get("bookableOrders");
  }

  private static TimereportForm form(ExtendedModelMap model) {
    return (TimereportForm) model.get("timereportForm");
  }

  private static TimereportForm booking(LocalDate date, Long suborderId, Long orderId) {
    var form = new TimereportForm();
    form.setReferenceday(date);
    form.setSuborderId(suborderId);
    form.setOrderId(orderId);
    return form;
  }

  private static SuborderSummary suborder(long id, String sign) {
    return new SuborderSummary(id, sign, null, false, false, TicketReferencePolicy.DEFAULT, Map.of());
  }

  private static Customerorder customerorder(long id, String sign, String shortdescription, String customerName) {
    var customer = new Customer();
    customer.setShortname(customerName);
    var order = mock(Customerorder.class);
    when(order.getId()).thenReturn(id);
    when(order.getSign()).thenReturn(sign);
    when(order.getShortdescription()).thenReturn(shortdescription);
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
