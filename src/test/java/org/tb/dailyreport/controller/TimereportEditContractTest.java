package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.preferences.DailyPreferenceService;
import org.tb.dailyreport.preferences.DurationInputMode;
import org.tb.dailyreport.preferences.TimereportPreferenceService;
import org.tb.dailyreport.preferences.TimereportPreferences;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.favorites.service.FavoriteService;
import org.tb.notification.service.NotificationService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.EmployeeorderService;
import org.tb.order.service.SuborderService;

/**
 * Beim Bearbeiten ergibt sich der Vertrag aus der Person der Buchung und dem Zieldatum, nicht aus
 * der gemerkten Auswahl (#1128). Bis dahin schrieb das Formular die Buchung stillschweigend auf den
 * Vertrag der gemerkten Person, etwa wenn in einem zweiten Tab eine andere Person ausgewählt war.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimereportEditContractTest {

  private static final long TIMEREPORT_ID = 3L;
  private static final long OWN_CONTRACT_ID = 42L;
  private static final long REMEMBERED_CONTRACT_ID = 99L;
  private static final long SUBORDER_ID = 5L;
  private static final long EMPLOYEE_ORDER_ID = 7L;
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
    when(timereportService.getEmployeecontractIdForUpdate(TIMEREPORT_ID, DATE)).thenReturn(OWN_CONTRACT_ID);
    when(timereportPreferenceService.getForCurrentUser())
        .thenReturn(new TimereportPreferences(null, DurationInputMode.DURATION, DurationInputMode.DURATION));
    when(messages.getMessage(anyString())).thenReturn("ok");
  }

  @Test
  void saving_an_edited_booking_keeps_it_with_its_own_person() {
    var employeeorder = mock(Employeeorder.class);
    when(employeeorder.getId()).thenReturn(EMPLOYEE_ORDER_ID);
    when(employeeorderService.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(OWN_CONTRACT_ID, SUBORDER_ID, DATE))
        .thenReturn(employeeorder);

    controller.update(TIMEREPORT_ID, REMEMBERED_CONTRACT_ID, editedBooking(), null, null, null, redirectAttributes,
        new ExtendedModelMap());

    verify(timereportService).updateTimereport(eq(TIMEREPORT_ID), eq(OWN_CONTRACT_ID), eq(EMPLOYEE_ORDER_ID), eq(DATE),
        anyString(), any(), anyBoolean(), eq(1L), eq(30L));
    verify(employeeorderService, never())
        .getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(eq(REMEMBERED_CONTRACT_ID), anyLong(), any());
  }

  /** Ein neues Datum im Bearbeiten-Formular bietet die Aufträge der Person der Buchung an. */
  @Test
  void the_edit_form_offers_the_orders_of_the_booked_person() {
    when(customerorderService.getCustomerordersWithValidEmployeeOrders(anyLong(), eq(DATE))).thenReturn(List.of());

    var model = new ExtendedModelMap();
    controller.refreshOrders(REMEMBERED_CONTRACT_ID, editedBooking(), model);

    assertThat(model.get("selectedContractId")).isEqualTo(OWN_CONTRACT_ID);
    verify(customerorderService).getCustomerordersWithValidEmployeeOrders(OWN_CONTRACT_ID, DATE);
  }

  /** Ohne Buchung bleibt es bei der gemerkten Auswahl: eine neue Buchung wird für sie angelegt. */
  @Test
  void the_create_form_offers_the_orders_of_the_remembered_person() {
    when(customerorderService.getCustomerordersWithValidEmployeeOrders(anyLong(), eq(DATE))).thenReturn(List.of());
    var form = editedBooking();
    form.setId(null);

    var model = new ExtendedModelMap();
    controller.refreshOrders(REMEMBERED_CONTRACT_ID, form, model);

    assertThat(model.get("selectedContractId")).isEqualTo(REMEMBERED_CONTRACT_ID);
  }

  private static TimereportForm editedBooking() {
    var form = new TimereportForm();
    form.setId(TIMEREPORT_ID);
    form.setReferenceday(DATE);
    form.setSuborderId(SUBORDER_ID);
    form.setDurationTime("1:30");
    form.setComment("comment");
    return form;
  }

}
