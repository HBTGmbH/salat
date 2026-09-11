package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;
import org.tb.jira.domain.JiraTicket;
import org.tb.jira.persistence.JiraTicketRepository;

/**
 * The optional ticket reference on a booking (#982): free text, with the tickets replicated for the
 * selected order offered while typing. Picking one stores its number and writes number and title
 * into an untouched comment - a comment somebody typed themselves stays as it is.
 *
 * <p>Books on a day of its own, as every E2E class does - the bookings are never cleaned up.
 */
class TicketReferenceE2ETest extends PlaywrightE2ETestBase {

  private static final LocalDate DAY = LocalDate.parse("2026-06-30");
  private static final LocalDate OTHER_DAY = LocalDate.parse("2026-07-01");
  private static final String TICKET_KEY = "ALPHA-4711";
  private static final String TICKET_SUMMARY = "Anmeldung schlägt bei langen Namen fehl";
  private static final String OTHER_TICKET_KEY = "ALPHA-4712";
  private static final String OTHER_TICKET_SUMMARY = "Export bricht bei großen Berichten ab";

  /** What a pick writes into the comment: the number, so the title does not stand there alone. */
  private static final String TICKET_COMMENT = TICKET_KEY + " - " + TICKET_SUMMARY;
  private static final String OTHER_TICKET_COMMENT = OTHER_TICKET_KEY + " - " + OTHER_TICKET_SUMMARY;

  @Autowired
  private JiraTicketRepository jiraTicketRepository;

  @BeforeAll
  void seedReplicatedTickets() {
    if (!jiraTicketRepository.findByCustomerorderSign(E2ETestData.CUSTOMERORDER_CONTOSO_SIGN).isEmpty()) {
      return;
    }
    saveTicket(4711L, TICKET_KEY, TICKET_SUMMARY);
    saveTicket(4712L, OTHER_TICKET_KEY, OTHER_TICKET_SUMMARY);
  }

  private void saveTicket(long jiraId, String key, String summary) {
    var ticket = new JiraTicket();
    ticket.setCustomerorderSign(E2ETestData.CUSTOMERORDER_CONTOSO_SIGN);
    ticket.setJiraId(jiraId);
    ticket.setKey(key);
    ticket.setSummary(summary);
    jiraTicketRepository.save(ticket);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
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
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_comment_that_is_already_filled_survives_the_pick(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, bookingFormPath(OTHER_DAY), page -> {
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);
      page.fill("#commentField", "Von Hand geschrieben");

      pickTicketSuggestion(page, TICKET_KEY);

      assertThat(page.locator("#commentField")).hasValue("Von Hand geschrieben");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
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
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_reference_that_matches_no_replicated_ticket_is_kept_as_typed(E2EBrowser browser) {
    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, bookingFormPath(OTHER_DAY), page -> {
      selectTomSelectOption(page, "suborderId", E2ETestData.SUBORDER_ALPHA_DEV_SIGN);

      ticketReferenceControl(page).click();
      page.locator("#ticketReference-ts-control").pressSequentially("EXTERN-99");
      // leaving the field is what turns the typed text into the value
      page.locator("#commentField").click();

      assertThat(ticketReferenceControl(page)).containsText("EXTERN-99");
      // and nothing was invented for the comment - there is no title for a ticket nobody knows
      assertThat(page.locator("#commentField")).isEmpty();
    });
  }

  private String bookingFormPath(LocalDate day) {
    return "/dailyreport/timereports/new?date=" + day;
  }

  private Locator ticketReferenceControl(Page page) {
    return page.locator("#ticketReference ~ .ts-wrapper .ts-control");
  }

  private void pickTicketSuggestion(Page page, String key) {
    ticketReferenceControl(page).click();
    page.locator("#ticketReference-ts-control").pressSequentially(key);
    page.locator("#ticketReference ~ .ts-wrapper .ts-dropdown .option")
        .filter(new Locator.FilterOptions().setHasText(key))
        .first()
        .click();
  }

}
