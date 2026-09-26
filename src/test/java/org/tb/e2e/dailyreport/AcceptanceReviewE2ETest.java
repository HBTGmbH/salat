package org.tb.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.tb.common.test.FixedClock;
import org.tb.e2e.E2EBrowser;
import org.tb.e2e.E2ETestData;
import org.tb.e2e.PlaywrightE2ETestBase;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.employee.persistence.EmployeecontractRepository;

/**
 * The overview before an acceptance (#1122): the people lead chooses the person on
 * {@code /acceptance}, no month after the release can be chosen, and the overview shows the released
 * bookings of the acceptance period — by order with their comments first, by day on request. The
 * acceptance happens from there, without a confirmation dialog. Whoever may not accept a contract —
 * a regular employee, or anybody for their own — gets neither the accept form nor the overview.
 *
 * <p>Each browser accepts a person of its own ({@link E2ETestData#acceptedEmployeeSign}), because a
 * period can be accepted only once. The clock stands after the accepted month.
 */
@FixedClock("2026-04-01T09:00:00")
class AcceptanceReviewE2ETest extends PlaywrightE2ETestBase {

  private static final String ALPHA_DEV = E2ETestData.CUSTOMERORDER_CONTOSO_SIGN + "/"
      + E2ETestData.SUBORDER_ALPHA_DEV_SIGN;
  private static final String GLOBEX_CONSULT = E2ETestData.CUSTOMERORDER_GLOBEX_SIGN + "/"
      + E2ETestData.SUBORDER_GLOBEX_CONSULT_SIGN;
  /** The days of the two bookings {@link E2ETestData} seeds for the accepted person. */
  private static final LocalDate EDITED_DAY = LocalDate.of(2026, 3, 30);
  private static final LocalDate OTHER_DAY = LocalDate.of(2026, 3, 31);
  /** The name {@link E2ETestData} gives {@link E2ETestData#EMPLOYEE_BL_SIGN}, a manager. */
  private static final String MANAGER_NAME = "Bernd Leitmann";

  @Autowired
  private EmployeeRepository employeeRepository;
  @Autowired
  private EmployeecontractRepository employeecontractRepository;

  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_people_lead_reviews_corrects_and_accepts_the_released_bookings(E2EBrowser browser) {
    String name = E2ETestData.acceptedEmployeeName(browser);
    String sign = E2ETestData.acceptedEmployeeSign(browser);
    String editedComment = E2ETestData.ACCEPTING_EDITED_COMMENT + " und nachgebessert";

    runAsUser(browser, E2ETestData.EMPLOYEE_PV_SIGN, "/acceptance", page -> {
      selectTomSelectOption(page, "employee-contract-select", name);
      assertThat(page).hasURL(Pattern.compile(".*/acceptance\\?.*fAcceptanceEmployeeContractId=\\d+.*"));
      // no month after the release can be chosen. The proposal is March either way: here the last
      // acceptance and the release fall into the same month, so that the month of the release is
      // proposed shows AcceptanceControllerTest, not this test
      Locator month = page.locator("#acceptance-accept-until");
      assertThat(month).hasValue(E2ETestData.ACCEPTING_MONTH);
      assertThat(month).hasAttribute("max", E2ETestData.ACCEPTING_MONTH);
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Abnahme prüfen")).click();

      assertThat(page).hasURL(Pattern.compile(".*/acceptance/accept/review\\?contractId=\\d+&until=2026-03$"));
      Locator summary = page.locator("#review-summary");
      assertThat(summary).containsText(name + " | " + sign);
      assertThat(summary).containsText("Abnahmezeitraum");
      assertThat(summary).containsText("27.03.2026 – 31.03.2026");
      // three working days of eight hours, 8 + 6 hours booked
      assertThat(summary).containsText("24:00");
      assertThat(summary).containsText("14:00");
      assertThat(summary).containsText("-10:00");
      assertThat(summary).containsText("Die Differenz geht in das Überstundenkonto ein");

      // by order is the default view, with the comments
      assertThat(viewLink(page, "Nach Auftrag")).hasAttribute("aria-current", "page");
      assertThat(orderGroup(page, ALPHA_DEV)).containsText(E2ETestData.ACCEPTING_EDITED_COMMENT);
      assertThat(orderGroup(page, ALPHA_DEV)).containsText("8:00");
      assertThat(orderGroup(page, GLOBEX_CONSULT)).containsText(E2ETestData.ACCEPTING_OTHER_COMMENT);
      assertThat(orderGroup(page, GLOBEX_CONSULT)).containsText("6:00");
      // the supervising people lead may change released bookings, so each of them has its edit link
      assertThat(bookingRow(page, E2ETestData.ACCEPTING_EDITED_COMMENT).locator("a[href*='/edit']")).hasCount(1);
      assertThat(bookingRow(page, E2ETestData.ACCEPTING_OTHER_COMMENT).locator("a[href*='/edit']")).hasCount(1);
      assertThat(page.locator("#review-action"))
          .containsText("Abgenommen werden die freigegebenen Buchungen von " + name + " vom 27.03.2026 bis 31.03.2026.");
      assertThat(page.locator("#review-action"))
          .containsText("Danach ist das Überstundenkonto bis zum 31.03.2026 festgeschrieben");

      // by day: the bookings under their day, and the Friday without a booking stands there, but is
      // no finding and offers no booking
      viewLink(page, "Nach Tag").click();
      assertThat(page).hasURL(Pattern.compile(".*/acceptance/accept/review\\?contractId=\\d+&until=2026-03&view=day#review-views$"));
      assertThat(viewLink(page, "Nach Tag")).hasAttribute("aria-current", "page");
      assertThat(day(page, EDITED_DAY)).containsText(E2ETestData.ACCEPTING_EDITED_COMMENT);
      assertThat(day(page, OTHER_DAY)).containsText(E2ETestData.ACCEPTING_OTHER_COMMENT);
      Locator friday = day(page, E2ETestData.ACCEPTING_DAY_WITHOUT_BOOKING);
      assertThat(friday).containsText("Keine Buchung");
      assertThat(friday.locator(".text-danger")).hasCount(0);
      assertThat(page.locator("a[href*='/timereports/new?']")).hasCount(0);
      assertThat(page.locator("#review-findings")).hasCount(0);

      // the supervising people lead corrects a released booking and comes back to it
      viewLink(page, "Nach Auftrag").click();
      bookingRow(page, E2ETestData.ACCEPTING_EDITED_COMMENT).locator("a[href*='/edit']").click();
      assertThat(page).hasURL(Pattern.compile(".*/dailyreport/timereports/\\d+/edit\\?.*"));
      page.fill("#commentField", editedComment);
      page.click("#timereportMainForm button[type=submit]");

      assertThat(page).hasURL(Pattern.compile(".*/acceptance/accept/review\\?contractId=\\d+&until=2026-03#tr-\\d+$"));
      assertThat(page.locator(".alert-success")).containsText("Buchung gespeichert.");
      assertThat(orderGroup(page, ALPHA_DEV)).containsText(editedComment);

      // accept without a dialog: the toast names the period, the page the new acceptance date
      acceptButton(page).click();

      assertThat(page).hasURL(Pattern.compile(".*/acceptance$"));
      assertThat(page.locator("#confirmModal")).not().isVisible();
      assertThat(page.locator(".alert-success")).containsText("Buchungen vom 27.03.2026 bis 31.03.2026 abgenommen.");
      Locator row = page.locator("tbody tr").filter(new Locator.FilterOptions().setHasText(name));
      assertThat(row.locator("td").nth(5)).containsText("2026-03-31");
    });
  }

  /** Accepting needs people lead rights; without them there is no overview either. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void a_regular_employee_does_not_see_the_overview(E2EBrowser browser) {
    long contractId = contractIdOf(E2ETestData.acceptedEmployeeSign(browser));

    runAsUser(browser, E2ETestData.EMPLOYEE_MA_SIGN, "/release", page -> {
      var response = page.navigate(urlWithLogin("/acceptance/accept/review?contractId=" + contractId
          + "&until=" + E2ETestData.ACCEPTING_MONTH, E2ETestData.EMPLOYEE_MA_SIGN));

      assertEquals(403, response.status());
    });
  }

  /**
   * Nobody accepts their own bookings, the management neither. It finds its own contract on
   * {@code /acceptance} like any other, but instead of the accept form a notice, while the release
   * stays offered; the overview itself stays closed to it as well.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("org.tb.e2e.PlaywrightE2ETestBase#browsers")
  void the_own_contract_shows_no_accept_form(E2EBrowser browser) {
    long ownContractId = contractIdOf(E2ETestData.EMPLOYEE_BL_SIGN);

    runAsUser(browser, E2ETestData.EMPLOYEE_BL_SIGN, "/acceptance", page -> {
      selectTomSelectOption(page, "employee-contract-select", MANAGER_NAME);
      assertThat(page).hasURL(Pattern.compile(".*/acceptance\\?.*fAcceptanceEmployeeContractId=" + ownContractId + "(&.*)?$"));
      assertThat(page.locator("h3.card-title").filter(new Locator.FilterOptions().setHasText(MANAGER_NAME)))
          .isVisible();

      assertThat(page.locator("#acceptance-accept-until")).hasCount(0);
      assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Abnahme prüfen"))).hasCount(0);
      assertThat(page.locator("#acceptance-accept-not-allowed"))
          .hasText("Die Buchungen dieses Vertrags kannst du nicht abnehmen. Eigene Buchungen nimmt die "
              + "zuständige People Lead oder die Geschäftsführung ab.");
      assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Freigabe prüfen"))).isVisible();

      var response = page.navigate(urlWithLogin("/acceptance/accept/review?contractId=" + ownContractId
          + "&until=" + E2ETestData.ACCEPTING_MONTH, E2ETestData.EMPLOYEE_BL_SIGN));

      assertEquals(403, response.status());
    });
  }

  /** The contract of a seeded person; each of them has exactly one. */
  private long contractIdOf(String sign) {
    var employee = employeeRepository.findBySign(sign).orElseThrow();
    return employeecontractRepository.findAllByEmployeeId(employee.getId()).getFirst().getId();
  }

  private static Locator bookingRow(Page page, String comment) {
    return page.locator("tr").filter(new Locator.FilterOptions().setHasText(comment));
  }

  private static Locator viewLink(Page page, String name) {
    return page.locator("#review-views").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(name));
  }

  private static Locator orderGroup(Page page, String completeOrderSign) {
    return page.locator(".card").filter(new Locator.FilterOptions()
        .setHas(page.locator("h3.card-title", new Page.LocatorOptions().setHasText(completeOrderSign))));
  }

  private static Locator day(Page page, LocalDate date) {
    return page.locator("#day-" + date);
  }

  private static Locator acceptButton(Page page) {
    return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Abnehmen").setExact(true));
  }
}
