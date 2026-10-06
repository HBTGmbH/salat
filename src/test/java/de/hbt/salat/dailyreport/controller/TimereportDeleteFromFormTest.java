package de.hbt.salat.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.common.exception.ErrorCode.TR_COMMITTED_TIME_REPORT_NOT_SELF;
import static de.hbt.salat.common.exception.ErrorCode.TR_TIME_REPORT_NOT_FOUND;

import java.time.Duration;
import java.time.LocalDate;
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
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper.ViewMessage;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
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
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.EmployeeorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * Löschen aus dem Bearbeiten-Formular (#1192). Wer welche Buchung nach ihrem Status löschen darf, entscheidet
 * {@code TimereportAuthorization.isWriteAllowed} und ist dort getestet; hier geht es darum, dass das Formular den Knopf
 * genau nach dieser Antwort anbietet, dass gelöscht wird wie auf jedem anderen Weg, und wohin es danach geht.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimereportDeleteFromFormTest {

  private static final long TIMEREPORT_ID = 3L;
  private static final long CONTRACT_ID = 42L;
  /** Bewusst nicht heute: sonst liest das Befüllen des Modells zusätzlich den Arbeitstag. */
  private static final LocalDate DATE = LocalDate.of(2026, 3, 2);
  private static final String DAILY_VIEW_OF_THE_DAY = "/dailyreport/daily?mode=daily&date=2026-03-02";

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
    var contract = mock(Employeecontract.class);
    when(contract.getId()).thenReturn(CONTRACT_ID);
    when(employeecontractService.getReadableEmployeecontract(CONTRACT_ID)).thenReturn(Optional.of(contract));
    when(timereportPreferenceService.getForCurrentUser())
        .thenReturn(new TimereportPreferences(null, DurationInputMode.DURATION, DurationInputMode.DURATION));
    when(customerorderService.getCustomerordersWithValidEmployeeOrders(anyLong(), eq(DATE))).thenReturn(List.of());
    when(timereportService.getTimereportById(TIMEREPORT_ID)).thenReturn(booking());
    when(messages.getMessage(anyString())).thenReturn("ok");
  }

  @Test
  void the_edit_form_offers_deleting_where_the_status_rule_allows_it_and_names_the_stored_booking() {
    when(timereportService.isWriteAllowed(TIMEREPORT_ID)).thenReturn(true);
    var model = new ExtendedModelMap();

    controller.editForm(TIMEREPORT_ID, CONTRACT_ID, null, model);

    assertThat(model.get("deletableTimereport")).isEqualTo(booking());
  }

  @Test
  void the_edit_form_offers_no_deleting_where_the_status_rule_forbids_it() {
    when(timereportService.isWriteAllowed(TIMEREPORT_ID)).thenReturn(false);
    var model = new ExtendedModelMap();

    controller.editForm(TIMEREPORT_ID, CONTRACT_ID, null, model);

    assertThat(model.containsAttribute("deletableTimereport")).isFalse();
  }

  @Test
  void the_create_form_never_offers_deleting() {
    var model = new ExtendedModelMap();

    controller.createForm(CONTRACT_ID, null, DATE, null, null, null, null, null, null, null, model);

    assertThat(model.containsAttribute("deletableTimereport")).isFalse();
    verify(timereportService, never()).isWriteAllowed(anyLong());
  }

  @Test
  void deleting_goes_back_where_the_form_came_from() {
    var target = controller.delete(TIMEREPORT_ID, "/dailyreport/list", redirectAttributes);

    assertThat(target).isEqualTo("redirect:/dailyreport/list");
    verify(timereportService).deleteTimereportById(TIMEREPORT_ID);
    verify(redirectAttributes).addFlashAttribute("toastSuccess", "ok");
  }

  @Test
  void without_a_safe_way_back_deleting_leads_to_the_day_of_the_booking() {
    assertThat(controller.delete(TIMEREPORT_ID, null, redirectAttributes))
        .isEqualTo("redirect:" + DAILY_VIEW_OF_THE_DAY);
    assertThat(controller.delete(TIMEREPORT_ID, "https://evil.example.com", redirectAttributes))
        .isEqualTo("redirect:" + DAILY_VIEW_OF_THE_DAY);
  }

  @Test
  void a_refused_deletion_leads_back_to_the_form_with_the_message_and_its_way_back() {
    var refused = new AuthorizationException(TR_COMMITTED_TIME_REPORT_NOT_SELF);
    doThrow(refused).when(timereportService).deleteTimereportById(TIMEREPORT_ID);
    when(errorCodeViewHelper.toViewMessages(refused))
        .thenReturn(List.of(new ViewMessage("errorcode", new Object[0], "nicht erlaubt")));

    var target = controller.delete(TIMEREPORT_ID, "/dailyreport/list", redirectAttributes);

    assertThat(target).isEqualTo("redirect:/dailyreport/timereports/3/edit?returnUrl=%2Fdailyreport%2Flist");
    verify(redirectAttributes).addFlashAttribute("toastError", "nicht erlaubt");
    verify(redirectAttributes, never()).addFlashAttribute(eq("toastSuccess"), any());
  }

  @Test
  void a_booking_that_is_gone_already_leads_straight_back_so_the_message_is_not_lost() {
    var gone = new InvalidDataException(TR_TIME_REPORT_NOT_FOUND);
    when(timereportService.getTimereportById(TIMEREPORT_ID)).thenReturn(null);
    doThrow(gone).when(timereportService).deleteTimereportById(TIMEREPORT_ID);
    when(errorCodeViewHelper.toViewMessages(gone))
        .thenReturn(List.of(new ViewMessage("errorcode", new Object[0], "nicht gefunden")));

    var target = controller.delete(TIMEREPORT_ID, "/dailyreport/list", redirectAttributes);

    // the form of a missing booking redirects again, and a second redirect drops the flash message
    assertThat(target).isEqualTo("redirect:/dailyreport/list");
    verify(redirectAttributes).addFlashAttribute("toastError", "nicht gefunden");
  }

  private static TimereportDTO booking() {
    return TimereportDTO.builder()
        .id(TIMEREPORT_ID)
        .referenceday(DATE)
        .employeecontractId(CONTRACT_ID)
        .customerorderId(1L)
        .suborderId(5L)
        .duration(Duration.ofMinutes(90))
        .taskdescription("comment")
        .build();
  }

}
