package de.hbt.salat.dailyreport.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
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
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
import de.hbt.salat.dailyreport.preferences.DailyPreferences;
import de.hbt.salat.dailyreport.preferences.DurationInputMode;
import de.hbt.salat.dailyreport.preferences.TimereportPreferenceService;
import de.hbt.salat.dailyreport.preferences.TimereportPreferences;
import de.hbt.salat.dailyreport.service.DailyService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.favorites.service.FavoriteService;
import de.hbt.salat.notification.service.NotificationService;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.EmployeeorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * Eine Buchung, die beim Bearbeiten auf einen anderen Tag gelegt wird, wird dort behandelt wie eine
 * neue (#1426): Fehlt dem Tag der Arbeitstag, wird er mit den Defaults angelegt. Bis dahin säte nur
 * das Anlegen, und das Speichern scheiterte am fehlenden Arbeitsbeginn.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimereportEditSeedingTest {

  private static final long TIMEREPORT_ID = 3L;
  private static final long EC_ID = 42L;
  private static final long SUBORDER_ID = 5L;
  private static final long EMPLOYEE_ORDER_ID = 7L;
  /** Bewusst nicht heute: sonst liest das Befüllen des Modells zusätzlich den Arbeitstag. */
  private static final LocalDate STORED_DATE = LocalDate.of(2026, 3, 2);
  private static final LocalDate NEW_DATE = STORED_DATE.plusDays(1);

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
  @Mock private RedirectAttributes redirectAttributes;

  @InjectMocks private TimereportController controller;

  @BeforeEach
  void setUp() {
    when(timereportService.getTimereportById(TIMEREPORT_ID))
        .thenReturn(TimereportDTO.builder().id(TIMEREPORT_ID).referenceday(STORED_DATE).build());
    when(timereportService.isWriteAllowed(TIMEREPORT_ID)).thenReturn(true);
    when(timereportService.getEmployeecontractIdForUpdate(anyLong(), any())).thenReturn(EC_ID);
    var employeeorder = mock(Employeeorder.class);
    when(employeeorder.getId()).thenReturn(EMPLOYEE_ORDER_ID);
    when(employeeorderService.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(anyLong(), anyLong(), any()))
        .thenReturn(employeeorder);
    when(dailyPreferenceService.getForEmployeeContractId(EC_ID))
        .thenReturn(new DailyPreferences(LocalTime.of(9, 0), true));
    when(timereportPreferenceService.getForCurrentUser())
        .thenReturn(new TimereportPreferences(null, DurationInputMode.DURATION, DurationInputMode.DURATION));
    when(messages.getMessage(anyString())).thenReturn("ok");
  }

  @Test
  void a_booking_moved_to_another_day_seeds_the_working_day_there() {
    update(bookingOn(NEW_DATE));

    var order = inOrder(workingdayService, timereportService);
    order.verify(workingdayService).seedWorkingday(EC_ID, NEW_DATE, 9, 0);
    order.verify(timereportService).updateTimereport(
        eq(TIMEREPORT_ID), anyLong(), anyLong(),
        eq(NEW_DATE), anyString(), any(), anyBoolean(),
        anyLong(), anyLong());
  }

  @Test
  void in_begin_end_mode_the_entered_begin_becomes_the_start_of_the_day() {
    var form = bookingOn(NEW_DATE);
    form.setDurationMode("beginEnd");
    form.setBeginTime("07:45");
    form.setEndTime("09:15");

    update(form);

    verify(workingdayService).seedWorkingday(EC_ID, NEW_DATE, 7, 45);
  }

  @Test
  void a_booking_staying_on_its_day_seeds_nothing() {
    update(bookingOn(STORED_DATE));

    verify(workingdayService, never()).seedWorkingday(anyLong(), any(), anyInt(), anyInt());
  }

  /** Den Fehler meldet dann das Speichern selbst; ein Arbeitstag bleibt nicht zurück. */
  @Test
  void a_booking_the_user_may_not_change_seeds_nothing() {
    when(timereportService.isWriteAllowed(TIMEREPORT_ID)).thenReturn(false);
    stubFormRerendering();

    update(bookingOn(NEW_DATE));

    verify(workingdayService, never()).seedWorkingday(anyLong(), any(), anyInt(), anyInt());
  }

  @Test
  void a_locked_target_day_seeds_nothing() {
    doThrow(new AuthorizationException(ErrorCode.AA_NOT_ATHORIZED))
        .when(timereportService).checkCreationAllowed(EC_ID, NEW_DATE);
    stubFormRerendering();

    update(bookingOn(NEW_DATE));

    verify(workingdayService, never()).seedWorkingday(anyLong(), any(), anyInt(), anyInt());
  }

  private void update(TimereportForm form) {
    controller.update(TIMEREPORT_ID, EC_ID, form, null, null, null, redirectAttributes, new ExtendedModelMap());
  }

  private static TimereportForm bookingOn(LocalDate date) {
    var form = new TimereportForm();
    form.setId(TIMEREPORT_ID);
    form.setReferenceday(date);
    form.setSuborderId(SUBORDER_ID);
    form.setDurationTime("1:30");
    form.setComment("comment");
    return form;
  }

  /** Was das erneute Rendern des Formulars im Fehlerfall zusätzlich liest. */
  private void stubFormRerendering() {
    when(errorCodeViewHelper.toViewMessages(any(ErrorCodeException.class))).thenReturn(List.of());
    var contract = mock(Employeecontract.class);
    when(contract.getId()).thenReturn(EC_ID);
    when(employeecontractService.getReadableEmployeecontract(EC_ID)).thenReturn(Optional.of(contract));
    when(customerorderService.getCustomerordersWithValidEmployeeOrders(anyLong(), any())).thenReturn(List.of());
    when(timereportService.getTimereportsByDateAndEmployeeContractId(anyLong(), any())).thenReturn(List.of());
  }

}
