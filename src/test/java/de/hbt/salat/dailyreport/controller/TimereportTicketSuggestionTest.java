package de.hbt.salat.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
import de.hbt.salat.dailyreport.preferences.DailyPreferences;
import de.hbt.salat.dailyreport.preferences.DurationInputMode;
import de.hbt.salat.dailyreport.preferences.TimereportPreferenceService;
import de.hbt.salat.dailyreport.preferences.TimereportPreferences;
import de.hbt.salat.dailyreport.service.DailyService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.TicketReferenceMode;
import de.hbt.salat.order.domain.TicketReferencePolicy;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.EmployeeorderService;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.order.service.SuborderSummary;

/**
 * Saving the booking form is held once when the comment names ticket keys that are no reference yet
 * (#1326): the form comes back offering them, and the answer — adopt a selection or save without —
 * saves without asking again.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportTicketSuggestionTest {

  private static final long CONTRACT_ID = 7L;
  private static final long LOGIN_EMPLOYEE_ID = 8L;
  private static final long ORDER_ID = 4L;
  private static final long SUBORDER_ID = 5L;
  private static final long EMPLOYEE_ORDER_ID = 11L;
  private static final LocalDate DATE = LocalDate.of(2026, 3, 2);

  @Mock private TimereportService timereportService;
  @Mock private EmployeecontractService employeecontractService;
  @Mock private CustomerorderService customerorderService;
  @Mock private SuborderService suborderService;
  @Mock private EmployeeorderService employeeorderService;
  @Mock private WorkingdayService workingdayService;
  @Mock private DailyService dailyService;
  @Mock private EmployeeService employeeService;
  @Mock private AuthorizedEmployee authorizedEmployee;
  @Mock private MessageSourceAccessor messages;
  @Mock private DailyPreferenceService dailyPreferenceService;
  @Mock private TimereportPreferenceService timereportPreferenceService;
  @Mock private RedirectAttributes redirectAttributes;

  @InjectMocks private TimereportController controller;

  @BeforeEach
  void setUp() {
    var employee = mock(Employee.class);
    when(employee.getId()).thenReturn(LOGIN_EMPLOYEE_ID);
    when(employeeService.getLoginEmployee()).thenReturn(employee);
    var contract = mock(Employeecontract.class);
    when(contract.getId()).thenReturn(CONTRACT_ID);
    when(contract.getEmployee()).thenReturn(employee);
    when(employeecontractService.getCurrentContract(LOGIN_EMPLOYEE_ID)).thenReturn(Optional.of(contract));
    when(employeecontractService.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract);
    when(timereportPreferenceService.getForCurrentUser())
        .thenReturn(new TimereportPreferences(null, DurationInputMode.DURATION, DurationInputMode.DURATION));
    when(dailyPreferenceService.getForEmployeeContractId(anyLong())).thenReturn(new DailyPreferences(LocalTime.of(9, 0), true));
    when(timereportService.getWorkableSerialDates(eq(DATE), anyInt())).thenReturn(List.of(DATE));
    when(messages.getMessage(anyString())).thenReturn("ok");
    var employeeorder = mock(Employeeorder.class);
    when(employeeorder.getId()).thenReturn(EMPLOYEE_ORDER_ID);
    when(employeeorderService.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(anyLong(), eq(SUBORDER_ID),
        eq(DATE))).thenReturn(employeeorder);

    var order = mock(Customerorder.class);
    when(order.getId()).thenReturn(ORDER_ID);
    when(order.getSign()).thenReturn("4711");
    when(order.getCustomer()).thenReturn(new Customer());
    when(order.getOrderType()).thenReturn(OrderType.STANDARD);
    when(customerorderService.getCustomerordersWithValidEmployeeOrders(CONTRACT_ID, DATE)).thenReturn(List.of(order));
    givenPolicy(TicketReferencePolicy.DEFAULT);
  }

  @Test
  void keys_in_the_comment_hold_the_save_and_are_offered() {
    var model = new ExtendedModelMap();

    var view = controller.create(null, booking("Analyse ABC-12 und abc-13, siehe ABC-12"), null, null, null, null,
        redirectAttributes, model);

    assertThat(view).isEqualTo("dailyreport/timereport-form");
    assertThat(model.get("ticketSuggestions")).isEqualTo(List.of("ABC-12", "ABC-13"));
    verify(timereportService, never()).createTimereports(anyLong(), anyLong(), any(), any(), any(), anyBoolean(),
        anyLong(), anyLong(), anyInt());
  }

  @Test
  void a_key_already_referenced_is_not_offered() {
    var form = booking("Analyse abc-12");
    form.setTicketReferences(new ArrayList<>(List.of("ABC-12")));

    controller.create(null, form, null, null, null, null, redirectAttributes, new ExtendedModelMap());

    verifyCreatedWith(List.of("ABC-12"));
  }

  @Test
  void the_ticked_keys_are_adopted_behind_the_references() {
    var form = booking("Analyse ABC-12 und ABC-13");
    form.setTicketReferences(new ArrayList<>(List.of("OPS-1")));
    form.setTicketSuggestionChoice("adopt");
    form.setAdoptedTicketKeys(new ArrayList<>(List.of("ABC-13")));

    controller.create(null, form, null, null, null, null, redirectAttributes, new ExtendedModelMap());

    verifyCreatedWith(List.of("OPS-1", "ABC-13"));
  }

  @Test
  void saving_without_adopting_asks_no_more() {
    var form = booking("Analyse ABC-12");
    form.setTicketSuggestionChoice("skip");
    form.setAdoptedTicketKeys(new ArrayList<>(List.of("ABC-12")));

    controller.create(null, form, null, null, null, null, redirectAttributes, new ExtendedModelMap());

    verifyCreatedWith(List.of());
  }

  @Test
  void nothing_is_offered_where_the_suborder_allows_no_reference() {
    givenPolicy(new TicketReferencePolicy(TicketReferenceMode.NONE, null));

    controller.create(null, booking("Analyse ABC-12"), null, null, null, null, redirectAttributes, new ExtendedModelMap());

    verifyCreatedWith(List.of());
  }

  @Test
  void nothing_is_offered_where_the_limit_is_reached() {
    givenPolicy(new TicketReferencePolicy(TicketReferenceMode.LIMITED, 1));
    var form = booking("Analyse ABC-12");
    form.setTicketReferences(new ArrayList<>(List.of("OPS-1")));

    controller.create(null, form, null, null, null, null, redirectAttributes, new ExtendedModelMap());

    verifyCreatedWith(List.of("OPS-1"));
  }

  /**
   * The suborders the contract may book on are read only where the comment names a key that is no
   * reference yet (#1402): they are a query per order, and saving read them every time since #1326.
   */
  @Test
  void a_save_with_nothing_to_offer_does_not_read_the_bookable_suborders() {
    var referenced = booking("Analyse ABC-12");
    referenced.setTicketReferences(new ArrayList<>(List.of("ABC-12")));
    var answered = booking("Analyse ABC-13");
    answered.setTicketSuggestionChoice("skip");

    controller.create(null, booking("Analyse ohne Ticket"), null, null, null, null, redirectAttributes,
        new ExtendedModelMap());
    controller.create(null, referenced, null, null, null, null, redirectAttributes, new ExtendedModelMap());
    controller.create(null, answered, null, null, null, null, redirectAttributes, new ExtendedModelMap());

    verify(timereportService, times(3)).createTimereports(anyLong(), anyLong(), any(), any(), any(), anyBoolean(),
        anyLong(), anyLong(), anyInt());
    verify(customerorderService, never()).getCustomerordersWithValidEmployeeOrders(anyLong(), any());
  }

  /** "Speichern und neu" is a request parameter; the held form hands it on to the answer. */
  @Test
  void a_held_save_and_new_is_remembered() {
    var model = new ExtendedModelMap();

    controller.create(null, booking("ABC-12"), null, null, null, true, redirectAttributes, model);

    assertThat(model.get("pendingSaveAndNew")).isEqualTo(true);
  }

  @Test
  void an_adopted_key_already_among_the_references_is_not_added_twice() {
    assertThat(TimereportController.withAdoptedKeys(List.of("ABC-1"), List.of("abc-1", "ABC-2", "")))
        .containsExactly("ABC-1", "ABC-2");
  }

  private void givenPolicy(TicketReferencePolicy policy) {
    when(suborderService.getSuborderSummaries(CONTRACT_ID, ORDER_ID, DATE))
        .thenReturn(List.of(new SuborderSummary(SUBORDER_ID, "4711/01", "Entwicklung", false, false, policy)));
  }

  private void verifyCreatedWith(List<String> references) {
    verify(timereportService).createTimereports(eq(CONTRACT_ID), eq(EMPLOYEE_ORDER_ID), eq(DATE), anyString(),
        eq(references), eq(false), eq(1L), eq(30L), eq(1));
  }

  private static TimereportForm booking(String comment) {
    var form = new TimereportForm();
    form.setReferenceday(DATE);
    form.setSuborderId(SUBORDER_ID);
    form.setDurationTime("1:30");
    form.setComment(comment);
    return form;
  }
}
