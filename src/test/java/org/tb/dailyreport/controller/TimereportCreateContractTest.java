package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.preferences.DailyPreferenceService;
import org.tb.dailyreport.preferences.DailyPreferences;
import org.tb.dailyreport.preferences.DurationInputMode;
import org.tb.dailyreport.preferences.TimereportPreferenceService;
import org.tb.dailyreport.preferences.TimereportPreferences;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.favorites.service.FavoriteService;
import org.tb.notification.service.NotificationService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.EmployeeorderService;
import org.tb.order.service.SuborderService;

/**
 * Eine neue Buchung für einen genannten Vertrag (#760). Die Übersicht vor der Freigabe legt an
 * einem Tag ohne Buchung eine für die Person an, die sie zeigt — und die ist nicht zwingend die
 * gemerkte Auswahl: gibt die Geschäftsführung über die Abnahme frei, nennt die gemerkte Auswahl
 * ihre eigene Person. Der Link nennt den Vertrag deshalb als Formularfeld
 * {@code employeecontractId} (ADR-0023), und das Formular trägt ihn bis zum Speichern.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportCreateContractTest {

  private static final long NAMED_CONTRACT_ID = 42L;
  private static final long REMEMBERED_CONTRACT_ID = 99L;
  private static final long TIMEREPORT_ID = 3L;
  private static final long OWN_CONTRACT_ID = 7L;
  private static final long SUBORDER_ID = 5L;
  private static final long EMPLOYEE_ORDER_ID = 11L;
  /** Bewusst nicht heute: sonst liest das Befüllen des Modells zusätzlich den Arbeitstag. */
  private static final LocalDate DATE = LocalDate.of(2026, 3, 2);

  @Mock private TimereportService timereportService;
  @Mock private EmployeecontractService employeecontractService;
  @Mock private CustomerorderService customerorderService;
  @Mock private SuborderService suborderService;
  @Mock private EmployeeorderService employeeorderService;
  @Mock private WorkingdayService workingdayService;
  @Mock private FavoriteService favoriteService;
  @Mock private EmployeeService employeeService;
  @Mock private MessageSourceAccessor messages;
  @Mock private ErrorCodeViewHelper errorCodeViewHelper;
  @Mock private DailyPreferenceService dailyPreferenceService;
  @Mock private TimereportPreferenceService timereportPreferenceService;
  @Mock private NotificationService notificationService;
  @Mock private AuthorizedUser authorizedUser;
  @Mock private AuthorizedEmployee authorizedEmployee;
  @Mock private RedirectAttributes redirectAttributes;

  @InjectMocks private TimereportController controller;

  @BeforeEach
  void setUp() {
    var named = contract(NAMED_CONTRACT_ID);
    when(employeecontractService.getEmployeecontractForView(NAMED_CONTRACT_ID)).thenReturn(named);
    when(employeecontractService.getEmployeecontractById(NAMED_CONTRACT_ID)).thenReturn(named);
    when(timereportService.getEmployeecontractIdForUpdate(TIMEREPORT_ID, DATE)).thenReturn(OWN_CONTRACT_ID);
    when(customerorderService.getCustomerordersWithValidEmployeeOrders(anyLong(), eq(DATE))).thenReturn(List.of());
    when(timereportPreferenceService.getForCurrentUser())
        .thenReturn(new TimereportPreferences(null, DurationInputMode.DURATION, DurationInputMode.DURATION));
    when(dailyPreferenceService.getForEmployeeContractId(anyLong())).thenReturn(new DailyPreferences(LocalTime.of(9, 0)));
    when(timereportService.getWorkableSerialDates(eq(DATE), anyInt())).thenReturn(List.of(DATE));
    when(messages.getMessage(anyString())).thenReturn("ok");
    var employeeorder = mock(Employeeorder.class);
    when(employeeorder.getId()).thenReturn(EMPLOYEE_ORDER_ID);
    when(employeeorderService.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(anyLong(), eq(SUBORDER_ID),
        eq(DATE))).thenReturn(employeeorder);
  }

  @Test
  void a_form_opened_for_a_named_contract_offers_its_orders_and_carries_it() {
    var model = new ExtendedModelMap();

    controller.createForm(REMEMBERED_CONTRACT_ID, NAMED_CONTRACT_ID, DATE, null, null, null, null, null, model);

    assertThat(model.get("selectedContractId")).isEqualTo(NAMED_CONTRACT_ID);
    assertThat(((TimereportForm) model.get("timereportForm")).getEmployeecontractId()).isEqualTo(NAMED_CONTRACT_ID);
    verify(customerorderService).getCustomerordersWithValidEmployeeOrders(NAMED_CONTRACT_ID, DATE);
  }

  @Test
  void without_a_named_contract_the_form_follows_the_remembered_one() {
    var model = new ExtendedModelMap();

    controller.createForm(REMEMBERED_CONTRACT_ID, null, DATE, null, null, null, null, null, model);

    assertThat(model.get("selectedContractId")).isEqualTo(REMEMBERED_CONTRACT_ID);
    assertThat(((TimereportForm) model.get("timereportForm")).getEmployeecontractId()).isNull();
    verify(employeecontractService, never()).getEmployeecontractForView(anyLong());
  }

  /** Der genannte Vertrag wird mit der Leseprüfung geladen: ein Link öffnet keinen fremden Vertrag. */
  @Test
  void a_named_contract_the_user_may_not_see_is_refused() {
    when(employeecontractService.getEmployeecontractForView(NAMED_CONTRACT_ID))
        .thenThrow(new AuthorizationException(AA_NOT_ATHORIZED));

    assertThatThrownBy(() -> controller.createForm(REMEMBERED_CONTRACT_ID, NAMED_CONTRACT_ID, DATE, null, null,
        null, null, null, new ExtendedModelMap()))
        .isInstanceOf(AuthorizationException.class);
  }

  @Test
  void saving_a_new_booking_books_on_the_named_contract() {
    controller.create(REMEMBERED_CONTRACT_ID, newBooking(NAMED_CONTRACT_ID), null, null, null, null,
        redirectAttributes, new ExtendedModelMap());

    verify(workingdayService).seedWorkingday(NAMED_CONTRACT_ID, DATE, 9, 0);
    verify(timereportService).createTimereports(eq(NAMED_CONTRACT_ID), eq(EMPLOYEE_ORDER_ID), eq(DATE), anyString(),
        any(), anyBoolean(), eq(1L), eq(30L), eq(1));
    verify(employeeorderService, never())
        .getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(eq(REMEMBERED_CONTRACT_ID), anyLong(), any());
  }

  @Test
  void save_and_new_opens_the_next_form_for_the_named_contract() {
    var view = controller.create(REMEMBERED_CONTRACT_ID, newBooking(NAMED_CONTRACT_ID), null, null,
        "/release/review?until=2026-03&view=day#day-2026-03-02", true, redirectAttributes, new ExtendedModelMap());

    assertThat(view).isEqualTo("redirect:/dailyreport/timereports/new?date=2026-03-02&employeecontractId=42"
        + "&returnUrl=%2Frelease%2Freview%3Funtil%3D2026-03%26view%3Dday%23day-2026-03-02");
  }

  /** Nach dem Speichern geht es zurück an den Tag in der Übersicht, aus der das Formular kam. */
  @Test
  void saving_returns_to_the_day_in_the_overview() {
    var view = controller.create(REMEMBERED_CONTRACT_ID, newBooking(NAMED_CONTRACT_ID), null, null,
        "/acceptance/release/review?contractId=42&until=2026-03&view=day#day-2026-03-02", null, redirectAttributes,
        new ExtendedModelMap());

    assertThat(view).isEqualTo("redirect:/acceptance/release/review?contractId=42&until=2026-03&view=day#day-2026-03-02");
  }

  /** Die Aktualisierungen per HTMX schicken das versteckte Feld mit und bleiben beim genannten Vertrag. */
  @Test
  void refreshing_the_orders_of_a_new_booking_keeps_the_named_contract() {
    var model = new ExtendedModelMap();

    controller.refreshOrders(REMEMBERED_CONTRACT_ID, newBooking(NAMED_CONTRACT_ID), model);

    assertThat(model.get("selectedContractId")).isEqualTo(NAMED_CONTRACT_ID);
    verify(customerorderService).getCustomerordersWithValidEmployeeOrders(NAMED_CONTRACT_ID, DATE);
  }

  @Test
  void refreshing_the_sidebar_of_a_new_booking_keeps_the_named_contract() {
    var model = new ExtendedModelMap();

    controller.refreshSidebar(REMEMBERED_CONTRACT_ID, newBooking(NAMED_CONTRACT_ID), model);

    assertThat(model.get("selectedContractId")).isEqualTo(NAMED_CONTRACT_ID);
  }

  /** Eine bearbeitete Buchung bleibt bei ihrer Person (#1128); ein genannter Vertrag ändert daran nichts. */
  @Test
  void an_edited_booking_ignores_a_named_contract() {
    var form = newBooking(NAMED_CONTRACT_ID);
    form.setId(TIMEREPORT_ID);

    controller.update(TIMEREPORT_ID, REMEMBERED_CONTRACT_ID, form, null, null, null, redirectAttributes,
        new ExtendedModelMap());

    verify(timereportService).updateTimereport(eq(TIMEREPORT_ID), eq(OWN_CONTRACT_ID), eq(EMPLOYEE_ORDER_ID), eq(DATE),
        anyString(), any(), anyBoolean(), eq(1L), eq(30L));
    verify(employeecontractService, never()).getEmployeecontractForView(anyLong());
  }

  private static TimereportForm newBooking(long employeecontractId) {
    var form = new TimereportForm();
    form.setEmployeecontractId(employeecontractId);
    form.setReferenceday(DATE);
    form.setSuborderId(SUBORDER_ID);
    form.setDurationTime("1:30");
    form.setComment("comment");
    return form;
  }

  private static Employeecontract contract(long id) {
    var employee = mock(Employee.class);
    when(employee.getName()).thenReturn("Erika Probe");
    when(employee.getSign()).thenReturn("epr");
    var contract = mock(Employeecontract.class);
    when(contract.getId()).thenReturn(id);
    when(contract.getEmployee()).thenReturn(employee);
    when(contract.getTimeString()).thenReturn("01.01.2026 - ");
    when(contract.getOpenEnd()).thenReturn(true);
    return contract;
  }
}
