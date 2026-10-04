package de.hbt.salat.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.E2ETestData;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;
import de.hbt.salat.jira.domain.JiraTicket;
import de.hbt.salat.jira.persistence.JiraTicketRepository;
import de.hbt.salat.order.persistence.CustomerorderRepository;

/**
 * The optional ticket references on a booking (#982, #1326): free text, with the tickets replicated for the
 * branch of the selected suborder offered while typing (#1025). Picking one stores its number and
 * writes number and title into an untouched comment - a comment somebody typed themselves stays as
 * it is.
 *
 * <p>The tickets here are seeded order-wide, so what they exercise is the top of the branch: the
 * suborder that is booked on carries no replication of its own and still gets them offered.
 *
 * <p>Books on a day of its own, as every E2E class does - the bookings are never cleaned up.
 */
class TicketReferenceE2ETest extends PlaywrightE2ETestBase {

  private static final LocalDate DAY = LocalDate.parse("2026-06-30");
  private static final LocalDate OTHER_DAY = LocalDate.parse("2026-07-01");
  private static final LocalDate PROPOSAL_DAY = LocalDate.parse("2026-07-02");
  private static final String TICKET_KEY = "ALPHA-4711";
  private static final String TICKET_SUMMARY = "Anmeldung schlägt bei langen Namen fehl";
  private static final String OTHER_TICKET_KEY = "ALPHA-4712";
  private static final String OTHER_TICKET_SUMMARY = "Export bricht bei großen Berichten ab";

  /** What a pick writes into the comment: the number, so the title does not stand there alone. */
  private static final String TICKET_COMMENT = TICKET_KEY + " - " + TICKET_SUMMARY;
  private static final String OTHER_TICKET_COMMENT = OTHER_TICKET_KEY + " - " + OTHER_TICKET_SUMMARY;

  @Autowired
  private JiraTicketRepository jiraTicketRepository;

  @Autowired
  private CustomerorderRepository customerorderRepository;

  @BeforeAll
  void seedReplicatedTickets() {
    var contosoId = customerorderRepository.findIdBySign(E2ETestData.CUSTOMERORDER_CONTOSO_SIGN).orElseThrow();
    if (!jiraTicketRepository.findInScope(contosoId, null).isEmpty()) {
      return;
    }
    saveTicket(contosoId, 4711L, TICKET_KEY, TICKET_SUMMARY);
    saveTicket(contosoId, 4712L, OTHER_TICKET_KEY, OTHER_TICKET_SUMMARY);
  }

  private void saveTicket(long customerorderId, long jiraId, String key, String summary) {
    var ticket = new JiraTicket();
    ticket.setCustomerorderId(customerorderId);
    ticket.setScopeSign(E2ETestData.CUSTOMERORDER_CONTOSO_SIGN);
    ticket.setJiraId(jiraId);
    ticket.setKey(key);
    ticket.setSummary(summary);
    jiraTicketRepository.save(ticket);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void picking_a_ticket_stores_its_number_and_fills_an_empty_comment(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, bookingFormPath(DAY), page -> {
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);

      pickTicketSuggestion(page, TICKET_KEY);

      // the number is what gets stored; the comment gets number and title
      assertThat(ticketReferenceControl(page)).containsText(TICKET_KEY);
      assertThat(page.locator("#commentField")).hasValue(TICKET_COMMENT);

      page.fill("#durationTime", "01:00");
      page.click("#timereportMainForm button[type=submit]");

      assertThat(page.locator("#daily-bookings-area").getByText(TICKET_KEY).first()).isVisible();
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_comment_that_is_already_filled_survives_the_pick(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, bookingFormPath(OTHER_DAY), page -> {
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
      page.fill("#commentField", "Von Hand geschrieben");

      pickTicketSuggestion(page, TICKET_KEY);

      assertThat(page.locator("#commentField")).hasValue("Von Hand geschrieben");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void switching_the_ticket_rewrites_a_comment_nobody_touched(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, bookingFormPath(OTHER_DAY), page -> {
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);

      pickTicketSuggestion(page, TICKET_KEY);
      assertThat(page.locator("#commentField")).hasValue(TICKET_COMMENT);

      // the comment still says what the first pick wrote, so it is not somebody's own text
      pickTicketSuggestion(page, OTHER_TICKET_KEY);

      assertThat(ticketReferenceControl(page)).containsText(OTHER_TICKET_KEY);
      assertThat(page.locator("#commentField")).hasValue(OTHER_TICKET_COMMENT);
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_reference_that_matches_no_replicated_ticket_is_kept_as_typed(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, bookingFormPath(OTHER_DAY), page -> {
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);

      ticketReferenceControl(page).click();
      page.locator("#ticketReferences-ts-control").pressSequentially("EXTERN-99");
      // leaving the field is what turns the typed text into the value
      page.locator("#commentField").click();

      assertThat(ticketReferenceControl(page)).containsText("EXTERN-99");
      // and nothing was invented for the comment - there is no title for a ticket nobody knows
      assertThat(page.locator("#commentField")).isEmpty();
    });
  }

  /**
   * #1326: a booking takes several references, and keys named in the comment are offered when saving.
   * Nothing is ticked in advance; the ticked one is added behind the picked reference.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void keys_in_the_comment_are_offered_on_saving_and_the_ticked_ones_adopted(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, bookingFormPath(PROPOSAL_DAY), page -> {
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
      pickTicketSuggestion(page, TICKET_KEY);
      page.fill("#commentField", "Vorschlag " + browser + ": Folgefehler in beta-17 und GAMMA-3");
      page.fill("#durationTime", "00:30");
      page.click("#timereportMainForm button[data-submit-shortcut]");

      var proposal = page.locator("[data-ticket-suggestions]");
      assertThat(proposal).isVisible();
      assertThat(proposal.locator("input[type=checkbox]")).hasCount(2);
      assertThat(proposal.locator("input[value='BETA-17']")).not().isChecked();
      assertThat(page.locator("#timereportMainForm button[data-submit-shortcut]")).isDisabled();

      proposal.locator("input[value='BETA-17']").check();
      proposal.locator("[data-adopt-button]").click();

      var row = page.locator("#daily-bookings-area tbody tr").filter(new Locator.FilterOptions()
          .setHasText("Vorschlag " + browser));
      assertThat(row.locator(".badge").filter(new Locator.FilterOptions().setHasText(TICKET_KEY)).first()).isVisible();
      assertThat(row.locator(".badge").filter(new Locator.FilterOptions().setHasText("BETA-17")).first()).isVisible();
      assertThat(row.locator(".badge").filter(new Locator.FilterOptions().setHasText("GAMMA-3"))).hasCount(0);
    });
  }

  private String bookingFormPath(LocalDate day) {
    return "/dailyreport/timereports/new?date=" + day;
  }

  private Locator ticketReferenceControl(Page page) {
    return page.locator("#ticketReferences ~ .ts-wrapper .ts-control");
  }

  private void pickTicketSuggestion(Page page, String key) {
    ticketReferenceControl(page).click();
    page.locator("#ticketReferences-ts-control").pressSequentially(key);
    page.locator("#ticketReferences ~ .ts-wrapper .ts-dropdown .option")
        .filter(new Locator.FilterOptions().setHasText(key))
        .first()
        .click();
  }

}
