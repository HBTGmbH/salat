package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.tb.common.viewhelper.ErrorCodeViewHelper.ViewMessage;
import org.tb.dailyreport.domain.TimereportDTO;
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
 * Das Buchungsformular übernimmt nur eine Rücksprungadresse, die die Prüfung besteht (#1133).
 *
 * <p>Das Formular gibt die Adresse als verstecktes Feld und als Ziel des Links „Abbrechen" aus. Bis
 * #1133 prüfte der Controller sie nur dort, wo er selbst weiterleitet; ins Modell kam sie
 * ungeprüft, und ein Link auf das Formular mit {@code returnUrl=javascript:…} oder einer fremden
 * Seite machte daraus das Ziel von „Abbrechen". Steht im Modell keine Adresse, führt „Abbrechen" in
 * die Tagesansicht des Buchungstags.
 *
 * <p>Formular und Weiterleitung nach dem Speichern verwerfen dieselben Adressen: die Prüfung steht
 * an genau einer Stelle, {@link ReturnUrls}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimereportFormReturnUrlTest {

  private static final long TIMEREPORT_ID = 3L;
  private static final long CONTRACT_ID = 42L;
  private static final long SUBORDER_ID = 5L;
  private static final long EMPLOYEE_ORDER_ID = 7L;
  /** Bewusst nicht heute: sonst liest das Befüllen des Modells zusätzlich den Arbeitstag. */
  private static final LocalDate DATE = LocalDate.of(2026, 3, 2);
  private static final String DAILY_VIEW_OF_THE_DAY = "/dailyreport/daily?mode=daily&date=2026-03-02";

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
    when(timereportPreferenceService.getForCurrentUser())
        .thenReturn(new TimereportPreferences(null, DurationInputMode.DURATION, DurationInputMode.DURATION));
    when(customerorderService.getCustomerordersWithValidEmployeeOrders(anyLong(), eq(DATE))).thenReturn(List.of());
    when(timereportService.getTimereportById(TIMEREPORT_ID)).thenReturn(booking());
    when(timereportService.getEmployeecontractIdForUpdate(TIMEREPORT_ID, DATE)).thenReturn(CONTRACT_ID);
    when(messages.getMessage(anyString())).thenReturn("ok");
    when(errorCodeViewHelper.toViewMessage(anyString()))
        .thenAnswer(call -> new ViewMessage(call.getArgument(0), new Object[0], "invalid"));
  }

  /**
   * Aus dem Issue: eine {@code javascript:}-Adresse und eine fremde Seite. Dazu die übrigen Formen,
   * die die Prüfung verwirft — schemalos auf einen fremden Host, ein Ziel der Anwendung außerhalb
   * der Tagesansicht und eine Adresse mit Zeilenumbruch, die in die Weiterleitung einen Header
   * einschöbe.
   */
  static Stream<String> unsafeReturnUrls() {
    return Stream.of(
        "javascript:alert(1)",
        "https://evil.example.com",
        "//evil.example.com/dailyreport/daily",
        "/management/employees",
        "/dailyreport/daily\r\nSet-Cookie: injected=1");
  }

  @ParameterizedTest
  @MethodSource("unsafeReturnUrls")
  void the_create_form_drops_an_unsafe_return_target(String returnUrl) {
    var model = new ExtendedModelMap();

    controller.createForm(CONTRACT_ID, null, DATE, null, null, null, null, returnUrl, model);

    assertThat(model.get("returnUrl")).isNull();
  }

  @ParameterizedTest
  @MethodSource("unsafeReturnUrls")
  void the_edit_form_drops_an_unsafe_return_target(String returnUrl) {
    var model = new ExtendedModelMap();

    controller.editForm(TIMEREPORT_ID, CONTRACT_ID, returnUrl, model);

    assertThat(model.get("returnUrl")).isNull();
  }

  /** Das versteckte Feld trägt die Adresse zurück: auch das neu ausgegebene Formular verwirft sie. */
  @ParameterizedTest
  @MethodSource("unsafeReturnUrls")
  void a_form_shown_again_after_a_failed_check_drops_an_unsafe_return_target(String returnUrl) {
    var form = editedBooking();
    form.setDurationTime("0:00");
    var model = new ExtendedModelMap();

    var view = controller.update(TIMEREPORT_ID, CONTRACT_ID, form, null, null, returnUrl, redirectAttributes, model);

    assertThat(view).isEqualTo("dailyreport/timereport-form");
    assertThat(model.get("returnUrl")).isNull();
  }

  @ParameterizedTest
  @MethodSource("unsafeReturnUrls")
  void saving_redirects_to_the_daily_view_of_the_day_instead_of_an_unsafe_return_target(String returnUrl) {
    var employeeorder = mock(Employeeorder.class);
    when(employeeorder.getId()).thenReturn(EMPLOYEE_ORDER_ID);
    when(employeeorderService.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(CONTRACT_ID, SUBORDER_ID, DATE))
        .thenReturn(employeeorder);

    var view = controller.update(TIMEREPORT_ID, CONTRACT_ID, editedBooking(), null, null, returnUrl,
        redirectAttributes, new ExtendedModelMap());

    assertThat(view).isEqualTo("redirect:" + DAILY_VIEW_OF_THE_DAY);
  }

  @Test
  void a_return_target_in_the_daily_view_reaches_the_form_unchanged() {
    var returnUrl = "/dailyreport/daily?mode=list&fMonth=3&fYear=2026";
    var model = new ExtendedModelMap();

    controller.editForm(TIMEREPORT_ID, CONTRACT_ID, returnUrl, model);

    assertThat(model.get("returnUrl")).isEqualTo(returnUrl);
  }

  /** Die Übersicht vor der Freigabe (#760) ist ein zulässiges Ziel, samt Sicht und Sprungmarke. */
  @Test
  void a_return_target_in_an_overview_reaches_the_form_unchanged() {
    var returnUrl = "/release/review?until=2026-03&view=day#day-2026-03-02";
    var model = new ExtendedModelMap();

    controller.createForm(CONTRACT_ID, null, DATE, null, null, null, null, returnUrl, model);

    assertThat(model.get("returnUrl")).isEqualTo(returnUrl);
  }

  private static TimereportDTO booking() {
    return TimereportDTO.builder()
        .id(TIMEREPORT_ID)
        .referenceday(DATE)
        .employeecontractId(CONTRACT_ID)
        .customerorderId(1L)
        .suborderId(SUBORDER_ID)
        .duration(Duration.ofMinutes(90))
        .durationhours(1)
        .durationminutes(30)
        .taskdescription("comment")
        .build();
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
