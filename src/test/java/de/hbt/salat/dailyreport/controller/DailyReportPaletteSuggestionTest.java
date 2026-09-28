package de.hbt.salat.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static de.hbt.salat.common.palette.PaletteCommand.ACCEPT;
import static de.hbt.salat.common.palette.PaletteCommand.BOOK;
import static de.hbt.salat.common.palette.PaletteCommand.CONTROLLING;
import static de.hbt.salat.common.palette.PaletteCommand.DAY;
import static de.hbt.salat.common.palette.PaletteCommand.MATRIX;
import static de.hbt.salat.common.palette.PaletteCommand.RELEASE;
import static de.hbt.salat.common.palette.PaletteParameter.CUSTOMERORDER;
import static de.hbt.salat.common.palette.PaletteParameter.MONTH;
import static de.hbt.salat.common.palette.PaletteParameter.PERSON;
import static de.hbt.salat.common.palette.PaletteParameter.SUBORDER;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.palette.PaletteCommand;
import de.hbt.salat.common.palette.PaletteParameter;
import de.hbt.salat.common.palette.PaletteQuery;
import de.hbt.salat.common.palette.PaletteSuggestion;
import de.hbt.salat.common.palette.PaletteSuggestionRequest;
import de.hbt.salat.common.palette.PaletteText;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.common.web.UiState;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.domain.PreviousBooking;
import de.hbt.salat.dailyreport.preferences.TimereportPreferenceService;
import de.hbt.salat.dailyreport.preferences.TimereportPreferences;
import de.hbt.salat.dailyreport.service.PublicholidayService;
import de.hbt.salat.dailyreport.service.ReleaseService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.domain.PersonSearchResult;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.employee.service.PersonSearchService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.EmployeeorderService;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.order.service.SuborderSummary;

/**
 * The values the command palette offers for the parameters of the commands whose pages the booking
 * module owns (#1158): the last working day, the suborders the booking form offers — the favourite
 * first, then what was booked lately, and after them, disabled, those the contract can book today
 * but not on the chosen day —, the persons of the matrix and of the acceptance, and the months the
 * release and acceptance pages propose.
 *
 * <p>Which contracts may be read, released or accepted is decided by {@link EmployeecontractService}
 * and {@link ReleaseService}; here they answer the way they do for the roles in question.
 */
@ExtendWith(MockitoExtension.class)
@FixedClock("2026-09-28T10:15:30")
@DisplayNameGeneration(ReplaceUnderscores.class)
class DailyReportPaletteSuggestionTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);
  private static final LocalDate AUGUST_FIRST = LocalDate.of(2026, 8, 1);
  private static final long OWN_EMPLOYEE_ID = 5L;
  private static final long OWN_CONTRACT_ID = 42L;
  private static final long TEAM_CONTRACT_ID = 43L;
  private static final long ORDER_ID = 11L;
  private static final long MAINTENANCE_ID = 501L;
  private static final long DEVELOPMENT_ID = 502L;
  private static final long TRAINING_ID = 503L;

  @Mock private UiState uiState;
  @Mock private AuthorizedEmployee authorizedEmployee;
  @Mock private EmployeecontractService employeecontractService;
  @Mock private EmployeeorderService employeeorderService;
  @Mock private TimereportAuthorization timereportAuthorization;
  @Mock private AuthorizedUser authorizedUser;
  @Mock private CustomerorderService customerorderService;
  @Mock private SuborderService suborderService;
  @Mock private TimereportPreferenceService timereportPreferenceService;
  @Mock private TimereportService timereportService;
  @Mock private PublicholidayService publicholidayService;
  @Mock private ReleaseService releaseService;
  @Mock private PersonSearchService personSearchService;

  @InjectMocks
  private DailyReportPaletteProvider provider;

  private final Employeecontract ownContract = contract(OWN_CONTRACT_ID, "Person P", "ppp");
  private final Employeecontract teamContract = contract(TEAM_CONTRACT_ID, "Person Q", "qqq");

  // --- the day ----------------------------------------------------------------------------------

  @Test
  void offers_the_last_working_day_for_the_day_of_buchen_and_tag() {
    when(publicholidayService.getLastWorkdayBefore(TODAY)).thenReturn(LocalDate.of(2026, 9, 25));

    assertThat(suggest(BOOK, PaletteParameter.DAY, "")).extracting(PaletteSuggestion::value).containsExactly("2026-09-25");
    assertThat(suggest(DAY, PaletteParameter.DAY, "")).extracting(PaletteSuggestion::value).containsExactly("2026-09-25");
  }

  @Test
  void answers_nothing_for_the_commands_of_other_modules() {
    assertThat(suggest(CONTROLLING, CUSTOMERORDER, "muster")).isEmpty();
    assertThat(suggest(MATRIX, PaletteParameter.DAY, "")).isEmpty();
    verifyNoInteractions(publicholidayService, customerorderService, personSearchService, releaseService);
  }

  // --- the suborder -----------------------------------------------------------------------------

  @Test
  void offers_the_favourite_first_then_what_was_booked_lately_then_the_rest() {
    ownCurrentContract();
    bookable(TODAY, summary(TRAINING_ID, "MUSTER-01.05", "Weiterbildung"), summary(DEVELOPMENT_ID, "MUSTER-01.04",
        "Weiterentwicklung"), summary(MAINTENANCE_ID, "MUSTER-01.03", "Wartung"));
    favourite(MAINTENANCE_ID);
    bookedLately(DEVELOPMENT_ID);

    var suggestions = suggest(BOOK, SUBORDER, "");

    assertThat(suggestions).extracting(PaletteSuggestion::label)
        .containsExactly("MUSTER-01.03", "MUSTER-01.04", "MUSTER-01.05");
    assertThat(suggestions).extracting(PaletteSuggestion::note).containsExactly(
        PaletteText.of("main.palette.suggestion.favorite"), PaletteText.of("main.palette.suggestion.recent"), null);
    assertThat(suggestions).extracting(PaletteSuggestion::value).containsExactly("501", "502", "503");
    assertThat(suggestions).extracting(PaletteSuggestion::detail)
        .containsExactly("Wartung", "Weiterentwicklung", "Weiterbildung");
  }

  @Test
  void finds_a_suborder_by_its_sign_its_description_and_its_customer() {
    ownCurrentContract();
    bookable(TODAY, summary(MAINTENANCE_ID, "MUSTER-01.03", "Wartung"), summary(DEVELOPMENT_ID, "ANDERS-02.01", "Entwicklung"));
    favourite(null);
    bookedLately();

    assertThat(labels(suggest(BOOK, SUBORDER, "wart"))).containsExactly("MUSTER-01.03");
    assertThat(labels(suggest(BOOK, SUBORDER, "01.03"))).containsExactly("MUSTER-01.03");
    assertThat(labels(suggest(BOOK, SUBORDER, "musterkunde"))).containsExactly("MUSTER-01.03", "ANDERS-02.01");
    assertThat(labels(suggest(BOOK, SUBORDER, "nichts"))).isEmpty();
  }

  /** The whole sign resolves a single word even where it begins another sign as well. */
  @Test
  void marks_the_suborder_whose_whole_sign_was_typed() {
    ownCurrentContract();
    bookable(TODAY, summary(MAINTENANCE_ID, "MUSTER-01", "Wartung"), summary(DEVELOPMENT_ID, "MUSTER-01.04", "Entwicklung"));
    favourite(null);
    bookedLately();

    assertThat(suggest(BOOK, SUBORDER, "muster-01")).extracting(PaletteSuggestion::exact).containsExactly(true, false);
  }

  @Test
  void says_that_a_suborder_demands_a_comment() {
    ownCurrentContract();
    when(customerorderService.getCustomerordersWithValidEmployeeOrders(OWN_CONTRACT_ID, TODAY)).thenReturn(List.of(order()));
    when(suborderService.getSuborderSummaries(OWN_CONTRACT_ID, ORDER_ID, TODAY))
        .thenReturn(List.of(new SuborderSummary(MAINTENANCE_ID, "MUSTER-01.03", "Wartung", true, false)));
    favourite(null);
    bookedLately();

    assertThat(suggest(BOOK, SUBORDER, "")).extracting(PaletteSuggestion::commentRequired).containsExactly(true);
  }

  /**
   * On 1 August the contract books only the training; maintenance it books today. Maintenance is
   * shown, so that the one looked for does not simply go missing, but disabled and with the reason.
   */
  @Test
  void offers_what_is_bookable_today_but_not_on_the_chosen_day_disabled_and_after_the_rest() {
    ownCurrentContract();
    bookable(AUGUST_FIRST, summary(TRAINING_ID, "MUSTER-01.05", "Weiterbildung"));
    bookable(TODAY, summary(MAINTENANCE_ID, "MUSTER-01.03", "Wartung"), summary(TRAINING_ID, "MUSTER-01.05", "Weiterbildung"));
    favourite(MAINTENANCE_ID);
    bookedLately();

    var suggestions = provider.suggest(new PaletteSuggestionRequest(BOOK, SUBORDER, PaletteQuery.of(""), AUGUST_FIRST, null));

    assertThat(suggestions).extracting(PaletteSuggestion::label).containsExactly("MUSTER-01.05", "MUSTER-01.03");
    assertThat(suggestions).extracting(PaletteSuggestion::disabled).containsExactly(false, true);
    assertThat(suggestions.get(1).note()).isEqualTo(PaletteText.of("main.palette.suggestion.notbookable", "01.08.2026"));
  }

  /** The form opens for the remembered selection where it may be read; so does the list. */
  @Test
  void offers_the_suborders_of_the_selected_contract() {
    when(uiState.getLongValue(DailyReportUiStateKeyContributor.EMPLOYEE_CONTRACT_ID)).thenReturn(TEAM_CONTRACT_ID);
    when(employeecontractService.getReadableEmployeecontract(TEAM_CONTRACT_ID)).thenReturn(Optional.of(teamContract));
    when(customerorderService.getCustomerordersWithValidEmployeeOrders(TEAM_CONTRACT_ID, TODAY)).thenReturn(List.of());
    favourite(null);
    when(timereportService.getPreviousBookings(TEAM_CONTRACT_ID, TODAY.plusDays(1))).thenReturn(List.of());

    assertThat(suggest(BOOK, SUBORDER, "")).isEmpty();
    verify(customerorderService).getCustomerordersWithValidEmployeeOrders(TEAM_CONTRACT_ID, TODAY);
  }

  @Test
  void offers_no_suborder_without_a_contract() {
    when(authorizedEmployee.getEmployeeId()).thenReturn(null);

    assertThat(suggest(BOOK, SUBORDER, "")).isEmpty();
    verifyNoInteractions(customerorderService, suborderService);
  }

  // --- the persons ------------------------------------------------------------------------------

  @Test
  void offers_the_persons_of_the_palette_search_for_the_matrix() {
    when(personSearchService.getPalettePersons(any())).thenReturn(List.of(
        person(TEAM_CONTRACT_ID, "qqq", "Person Q")));

    var suggestions = suggest(MATRIX, PERSON, "qqq");

    assertThat(suggestions).containsExactly(new PaletteSuggestion("43", "Person Q", "qqq", null, false, false, true));
  }

  @Test
  void offers_nobody_to_accept_without_the_role_people_lead() {
    when(authorizedUser.isPeopleLead()).thenReturn(false);

    assertThat(suggest(ACCEPT, PERSON, "")).isEmpty();
    assertThat(suggest(ACCEPT, MONTH, "")).isEmpty();
    verifyNoInteractions(employeecontractService, releaseService);
  }

  /** A people lead's own contract is in the team list, but they may not accept for themselves. */
  @Test
  void offers_a_people_lead_the_team_members_they_may_accept_for() {
    when(authorizedUser.isPeopleLead()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(false);
    when(authorizedEmployee.getEmployeeId()).thenReturn(OWN_EMPLOYEE_ID);
    when(employeecontractService.getTeamContractsIncludingExpired(OWN_EMPLOYEE_ID)).thenReturn(List.of(ownContract, teamContract));
    when(releaseService.isAcceptAllowed(OWN_CONTRACT_ID)).thenReturn(false);
    when(releaseService.isAcceptAllowed(TEAM_CONTRACT_ID)).thenReturn(true);

    assertThat(suggest(ACCEPT, PERSON, "")).extracting(PaletteSuggestion::value).containsExactly("43");
    assertThat(suggest(ACCEPT, PERSON, "qqq")).extracting(PaletteSuggestion::exact).containsExactly(true);
  }

  @Test
  void offers_a_manager_every_visible_contract_they_may_accept_for() {
    when(authorizedUser.isPeopleLead()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(true);
    when(employeecontractService.getVisibleEmployeeContractsForAuthorizedUser()).thenReturn(List.of(ownContract, teamContract));
    when(releaseService.isAcceptAllowed(OWN_CONTRACT_ID)).thenReturn(true);
    when(releaseService.isAcceptAllowed(TEAM_CONTRACT_ID)).thenReturn(true);

    assertThat(suggest(ACCEPT, PERSON, "person")).extracting(PaletteSuggestion::label).containsExactly("Person P", "Person Q");
    assertThat(suggest(ACCEPT, PERSON, "q")).extracting(PaletteSuggestion::label).containsExactly("Person Q");
  }

  // --- the months -------------------------------------------------------------------------------

  @Test
  void proposes_the_next_open_month_of_the_contract_the_release_page_shows() {
    ownCurrentContract();
    ownContract.setReportReleaseDate(LocalDate.of(2026, 8, 31));
    when(releaseService.isReleaseAllowed(OWN_CONTRACT_ID)).thenReturn(true);

    assertThat(suggest(RELEASE, MONTH, "")).containsExactly(PaletteSuggestion.of("2026-09", "2026-09", null,
        PaletteText.of("main.palette.suggestion.month.release.since", "31.08.2026")));
  }

  @Test
  void proposes_the_first_month_of_a_contract_released_never_yet() {
    ownCurrentContract();
    when(releaseService.isReleaseAllowed(OWN_CONTRACT_ID)).thenReturn(true);

    assertThat(suggest(RELEASE, MONTH, "")).containsExactly(PaletteSuggestion.of("2024-01", "2024-01", null,
        PaletteText.of("main.palette.suggestion.month.release")));
  }

  @Test
  void proposes_no_month_where_the_release_page_would_refuse_the_contract() {
    ownCurrentContract();
    when(releaseService.isReleaseAllowed(OWN_CONTRACT_ID)).thenReturn(false);

    assertThat(suggest(RELEASE, MONTH, "")).isEmpty();
  }

  @Test
  void proposes_the_month_of_the_release_for_acceptance() {
    acceptFor(teamContract);
    teamContract.setReportReleaseDate(LocalDate.of(2026, 8, 31));
    teamContract.setReportAcceptanceDate(LocalDate.of(2026, 7, 31));

    assertThat(accept()).containsExactly(PaletteSuggestion.of("2026-08", "2026-08", null,
        PaletteText.of("main.palette.suggestion.month.accept.since", "31.07.2026")));
  }

  /** Nothing released beyond the last acceptance: the page proposes an accepted month, and so does the palette. */
  @Test
  void calls_an_accepted_month_accepted() {
    acceptFor(teamContract);
    teamContract.setReportReleaseDate(LocalDate.of(2026, 8, 31));
    teamContract.setReportAcceptanceDate(LocalDate.of(2026, 8, 31));

    assertThat(accept()).extracting(PaletteSuggestion::note)
        .containsExactly(PaletteText.of("main.palette.suggestion.month.accept.done", "31.08.2026"));
  }

  @Test
  void proposes_no_month_to_accept_for_a_contract_the_user_may_not_accept() {
    when(authorizedUser.isPeopleLead()).thenReturn(true);
    when(releaseService.isAcceptAllowed(TEAM_CONTRACT_ID)).thenReturn(false);

    assertThat(accept()).isEmpty();
    verify(employeecontractService, never()).getEmployeecontractById(TEAM_CONTRACT_ID);
  }

  // --- helpers ----------------------------------------------------------------------------------

  private List<PaletteSuggestion> suggest(PaletteCommand command, PaletteParameter parameter, String text) {
    return provider.suggest(new PaletteSuggestionRequest(command, parameter, PaletteQuery.of(text), null, null));
  }

  private List<PaletteSuggestion> accept() {
    return provider.suggest(new PaletteSuggestionRequest(ACCEPT, MONTH, PaletteQuery.of(""), null, TEAM_CONTRACT_ID));
  }

  private void acceptFor(Employeecontract contract) {
    when(authorizedUser.isPeopleLead()).thenReturn(true);
    when(releaseService.isAcceptAllowed(contract.getId())).thenReturn(true);
    when(employeecontractService.getEmployeecontractById(contract.getId())).thenReturn(contract);
  }

  private static List<String> labels(List<PaletteSuggestion> suggestions) {
    return suggestions.stream().map(PaletteSuggestion::label).toList();
  }

  private void ownCurrentContract() {
    lenient().when(uiState.getLongValue(DailyReportUiStateKeyContributor.EMPLOYEE_CONTRACT_ID)).thenReturn(null);
    when(authorizedEmployee.getEmployeeId()).thenReturn(OWN_EMPLOYEE_ID);
    when(employeecontractService.getCurrentContract(OWN_EMPLOYEE_ID)).thenReturn(Optional.of(ownContract));
  }

  private void bookable(LocalDate date, SuborderSummary... summaries) {
    when(customerorderService.getCustomerordersWithValidEmployeeOrders(OWN_CONTRACT_ID, date)).thenReturn(List.of(order()));
    when(suborderService.getSuborderSummaries(OWN_CONTRACT_ID, ORDER_ID, date)).thenReturn(List.of(summaries));
  }

  private void favourite(Long suborderId) {
    when(timereportPreferenceService.getForCurrentUser()).thenReturn(new TimereportPreferences(suborderId, null, null));
  }

  /** Bookings of the last days, one per suborder, the latest first. */
  private void bookedLately(long... suborderIds) {
    var bookings = new ArrayList<PreviousBooking>();
    for (long suborderId : suborderIds) {
      long employeeorderId = 900 + suborderId;
      bookings.add(new PreviousBooking(employeeorderId, "", null, Duration.ofHours(1)));
      var suborder = new Suborder();
      setField(suborder, "id", suborderId);
      var employeeorder = new Employeeorder();
      employeeorder.setSuborder(suborder);
      when(employeeorderService.getEmployeeorderById(employeeorderId)).thenReturn(employeeorder);
    }
    when(timereportService.getPreviousBookings(OWN_CONTRACT_ID, TODAY.plusDays(1))).thenReturn(bookings);
  }

  private static SuborderSummary summary(long id, String sign, String description) {
    return new SuborderSummary(id, sign, description, false, false);
  }

  private static Customerorder order() {
    var customer = new Customer();
    customer.setShortname("MUSTERKUNDE");
    var order = new Customerorder();
    setField(order, "id", ORDER_ID);
    order.setSign("MUSTER");
    order.setShortdescription("Rahmenvertrag");
    order.setCustomer(customer);
    return order;
  }

  private static Employeecontract contract(long id, String name, String sign) {
    var employee = new Employee();
    employee.setFirstname(name.split(" ")[0]);
    employee.setLastname(name.split(" ")[1]);
    employee.setSign(sign);
    var contract = new Employeecontract();
    setField(contract, "id", id);
    contract.setEmployee(employee);
    contract.setValidFrom(LocalDate.of(2024, 1, 1));
    return contract;
  }

  /** A person as the palette's person search answers with it. */
  private static PersonSearchResult person(long contractId, String sign, String name) {
    return new PersonSearchResult(contractId, contractId + 100, sign, name, LocalDate.of(2024, 1, 1), null, false, true);
  }
}
