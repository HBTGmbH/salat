package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.preferences.DailyPreferenceService;
import org.tb.dailyreport.preferences.DailyPreferences;
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
 * Der Mitarbeiterauftrag wird aufgelöst, bevor die Serientage gesät werden (#1111).
 *
 * <p>Seit das Anlegen eines Arbeitstags in einer eigenen Transaktion festgeschrieben wird, deckt
 * kein Rollback des Aufrufers mehr zu, was in der falschen Reihenfolge steht: Säte die Schleife
 * zuerst, blieben bei einem fehlenden Mitarbeiterauftrag die Arbeitstage stehen — ohne die
 * Buchungen, für die sie vorbereitet wurden. Ein fehlender Mitarbeiterauftrag ist dabei ein
 * gewöhnlicher Ausgang der Eingabeprüfung, kein Ausnahmefall.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimereportSerialSeedingOrderTest {

  private static final long EC_ID = 42L;
  private static final long SUBORDER_ID = 5L;
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

  @Test
  void a_missing_employee_order_leaves_no_workingday_behind() {
    when(employeeorderService.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(EC_ID, SUBORDER_ID, DATE))
        .thenReturn(null);
    stubFormRerendering();

    var view = create(threeSerialDays());

    // Die Eingabeprüfung endet im Formular, nicht auf der Fehlerseite …
    assertThat(view).isEqualTo("dailyreport/timereport-form");
    // … und kein Serientag ist angelegt worden.
    verify(workingdayService, never()).seedWorkingday(anyLong(), any(), anyInt(), anyInt());
  }

  @Test
  void the_employee_order_is_resolved_before_the_days_are_seeded() {
    var employeeorder = mock(Employeeorder.class);
    when(employeeorder.getId()).thenReturn(7L);
    when(employeeorderService.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(EC_ID, SUBORDER_ID, DATE))
        .thenReturn(employeeorder);
    when(dailyPreferenceService.getForEmployeeContractId(EC_ID))
        .thenReturn(new DailyPreferences(LocalTime.of(9, 0)));
    when(timereportService.getWorkableSerialDates(eq(DATE), anyInt()))
        .thenReturn(List.of(DATE, DATE.plusDays(1), DATE.plusDays(2)));
    when(messages.getMessage(anyString())).thenReturn("ok");

    create(threeSerialDays());

    var order = inOrder(employeeorderService, workingdayService);
    order.verify(employeeorderService)
        .getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(EC_ID, SUBORDER_ID, DATE);
    order.verify(workingdayService, times(3)).seedWorkingday(anyLong(), any(), anyInt(), anyInt());
  }

  private String create(TimereportForm form) {
    return controller.create(EC_ID, form, null, null, null, null, redirectAttributes, new ExtendedModelMap());
  }

  private static TimereportForm threeSerialDays() {
    var form = new TimereportForm();
    form.setReferenceday(DATE);
    form.setSuborderId(SUBORDER_ID);
    form.setDurationTime("1:30");
    form.setNumberOfSerialDays(3);
    return form;
  }

  /** Was das erneute Rendern des Formulars im Fehlerfall zusätzlich liest. */
  private void stubFormRerendering() {
    when(errorCodeViewHelper.toViewMessages(any(ErrorCodeException.class))).thenReturn(List.of());
    when(timereportPreferenceService.getForCurrentUser())
        .thenReturn(new TimereportPreferences(null, DurationInputMode.DURATION, DurationInputMode.DURATION));
    when(customerorderService.getCustomerordersWithValidEmployeeOrders(EC_ID, DATE)).thenReturn(List.of());
    when(timereportService.getTimereportsByDateAndEmployeeContractId(EC_ID, DATE)).thenReturn(List.of());
    when(timereportService.getRecentBookings(EC_ID, SUBORDER_ID)).thenReturn(List.of());
  }

}
