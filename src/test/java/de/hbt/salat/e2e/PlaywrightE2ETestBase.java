package de.hbt.salat.e2e;

import static org.mockito.Mockito.when;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.customer.persistence.CustomerRepository;
import de.hbt.salat.dailyreport.persistence.PublicholidayRepository;
import de.hbt.salat.dailyreport.persistence.ReferencedayRepository;
import de.hbt.salat.dailyreport.persistence.TimereportRepository;
import de.hbt.salat.dailyreport.persistence.WorkingdayRepository;
import de.hbt.salat.employee.persistence.EmployeeRepository;
import de.hbt.salat.employee.persistence.EmployeecontractRepository;
import de.hbt.salat.order.persistence.CustomerorderRepository;
import de.hbt.salat.order.persistence.EmployeeorderRepository;
import de.hbt.salat.order.persistence.SuborderRepository;

/**
 * Base class for Playwright-driven E2E tests against the {@code dailyreport} module.
 *
 * <p>Starts the real Spring Boot application ({@code webEnvironment = RANDOM_PORT}) with the
 * H2 test datasource ({@code unittest} profile) and the pre-authenticated dev login
 * ({@code local} profile, see {@link de.hbt.salat.auth.configuration.LocalDevSecurityConfiguration}).
 * Master data (customers, orders, employees, ...) is seeded once per test run via
 * {@link E2ETestData}.
 *
 * <p><b>The day of the tests:</b> {@link FixedClock} is inherited, so a subclass without an
 * annotation of its own runs on {@link #FIXED_NOW}, Thursday, 2026-06-25 (#1173). A class that
 * needs another day sets its own {@code @FixedClock}, on the class or on a single method. The seed
 * runs once, in the {@code @BeforeAll} of the first class, on that class's day; of that day it
 * reads only the year, for the sign of the vacation suborder. Every class day lies in 2026; one
 * outside it would change that sign for all classes that run after it.
 *
 * <p><b>Shared database state:</b> all E2E test classes run against the same H2 database, and
 * the bookings a test creates are never cleaned up — they stay visible to every test class that
 * runs afterwards, and a {@code @ParameterizedTest} over {@link #browsers()} creates them once
 * per browser. Tests must therefore assert on the data they created themselves instead of on
 * "the first row/cell" of a view (#846). Two conventions keep that manageable:
 * <ul>
 *   <li>address elements by something unique to the test (its suborder, its comment text)
 *       rather than by position,</li>
 *   <li>prefer a dedicated day or month per test class, so month-spanning views such as the
 *       matrix overview do not mix bookings of different classes in the first place.</li>
 * </ul>
 *
 * <p><b>Run one browser per run, in separate runs:</b> {@code -De2e.browsers=chrome} and then
 * {@code -De2e.browsers=firefox}, never both browsers in one run. Without the property
 * {@link #browsers()} expands to every browser inside one JVM and one database, so the second
 * browser inherits the bookings of the first — a test asserting on a per-day total then reads the
 * sum of both runs and fails for a reason that has nothing to do with the code under test. The CI
 * matrix passes one browser per runner, so such a failure never shows up there; see AGENTS.md,
 * "Testing and Quality".
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "management.health.mail.enabled=false")
@ActiveProfiles({"unittest", "local"})
@FixedClock(PlaywrightE2ETestBase.FIXED_NOW)
@TestInstance(Lifecycle.PER_CLASS)
public abstract class PlaywrightE2ETestBase {

  static final String FIXED_NOW = "2026-06-25T10:15:30";

  /**
   * Browsers to run each {@code @ParameterizedTest} against. Defaults to every
   * {@link E2EBrowser} (local full-coverage runs); a CI matrix leg narrows this to a single
   * browser via {@code -De2e.browsers=chrome} (or {@code firefox}) so each browser gets its
   * own job/report without duplicating test code.
   */
  protected static Stream<E2EBrowser> browsers() {
    String property = System.getProperty("e2e.browsers");
    if (property == null || property.isBlank()) {
      return Arrays.stream(E2EBrowser.values());
    }
    return Arrays.stream(property.split(","))
        .map(String::trim)
        .map(String::toUpperCase)
        .map(E2EBrowser::valueOf);
  }

  @LocalServerPort
  private int port;

  @Autowired
  private CustomerRepository customerRepository;
  @Autowired
  private CustomerorderRepository customerorderRepository;
  @Autowired
  private SuborderRepository suborderRepository;
  @Autowired
  private EmployeeRepository employeeRepository;
  @Autowired
  private EmployeecontractRepository employeecontractRepository;
  @Autowired
  private EmployeeorderRepository employeeorderRepository;
  @Autowired
  private SalatUserRepository salatUserRepository;
  @Autowired
  private PublicholidayRepository publicholidayRepository;
  @Autowired
  private ReferencedayRepository referencedayRepository;
  @Autowired
  private TimereportRepository timereportRepository;
  @Autowired
  private WorkingdayRepository workingdayRepository;

  // no SMTP server is available in the E2E environment; release/acceptance/sharing flows send
  // mail as a side effect, so the sender is stubbed out rather than left to fail with a raw
  // RuntimeException (see MailService.sendEmail)
  @MockitoBean(reset = MockReset.NONE)
  private JavaMailSender mailSender;

  private Playwright playwright;

  // one browser per E2EBrowser and class, launched on first use: without -De2e.browsers a class
  // runs both browsers, and launching one per test cost Firefox about 2 s each (#1169); every test
  // still gets a fresh BrowserContext, which shares no cookies, storage or cache with another
  private final Map<E2EBrowser, Browser> launchedBrowsers = new EnumMap<>(E2EBrowser.class);

  @BeforeAll
  void startPlaywrightAndSeedData() {
    when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

    playwright = Playwright.create();
    // runs on the day of the class, which FixedClockExtension pins before @BeforeAll; of that day
    // the seed reads only the year, for the sign of the Urlaub suborder
    E2ETestData.seedIfNeeded(customerRepository, customerorderRepository, suborderRepository,
        employeeRepository, employeecontractRepository, employeeorderRepository, salatUserRepository,
        publicholidayRepository, referencedayRepository, timereportRepository, workingdayRepository);
  }

  @AfterAll
  void stopPlaywright() {
    launchedBrowsers.values().forEach(Browser::close);
    launchedBrowsers.clear();
    playwright.close();
  }

  private String urlFor(String path, String employeeSign) {
    String separator = path.contains("?") ? "&" : "?";
    return "http://localhost:" + port + path + separator + "login-name=" + employeeSign;
  }

  /**
   * Opens a fresh context in the given browser, logs in as {@code employeeSign} by navigating to
   * {@code startPath} with {@code ?login-name=...}, and runs {@code testBody} against the
   * resulting page. Every subsequent {@code page.navigate(...)} call in the test body should
   * keep appending {@code login-name} (see {@link #urlFor}) rather than relying solely on the
   * {@code salat_dev_login} cookie, since it is marked {@code Secure} and cookie persistence
   * over plain {@code http://localhost} is browser-dependent.
   */
  protected void runAsUser(E2EBrowser browser, String employeeSign, String startPath, Consumer<Page> testBody) {
    // pin the locale so assertions on rendered (German) text are deterministic regardless of
    // the browser's own default Accept-Language
    runAsUser(browser, employeeSign, startPath, "de-DE", testBody);
  }

  /**
   * Same as {@link #runAsUser(E2EBrowser, String, String, Consumer)}, but with an explicit browser
   * locale. The locale reaches the application as {@code Accept-Language}, which
   * {@code CookieLocaleResolver} falls back to as long as no locale cookie is set - so
   * {@code "en-US"} renders the English UI.
   */
  protected void runAsUser(E2EBrowser browser, String employeeSign, String startPath, String locale,
      Consumer<Page> testBody) {
    runInContext(browser, employeeSign, startPath, new Browser.NewContextOptions().setLocale(locale), testBody);
  }

  /**
   * Same as {@link #runAsUser(E2EBrowser, String, String, Consumer)}, but on a device with a touch
   * screen, so that {@link Locator#tap()} is available. Only {@code hasTouch} is set: Firefox does
   * not support {@code isMobile}, and the viewport stays the desktop one the other tests use.
   */
  protected void runOnTouchDevice(E2EBrowser browser, String employeeSign, String startPath,
      Consumer<Page> testBody) {
    runInContext(browser, employeeSign, startPath,
        new Browser.NewContextOptions().setLocale("de-DE").setHasTouch(true), testBody);
  }

  private void runInContext(E2EBrowser browser, String employeeSign, String startPath,
      Browser.NewContextOptions contextOptions, Consumer<Page> testBody) {
    Browser b = launchedBrowsers.computeIfAbsent(browser, it -> it.launch(playwright));
    try (BrowserContext context = b.newContext(contextOptions)) {
      Page page = context.newPage();
      page.navigate(urlFor(startPath, employeeSign));
      testBody.accept(page);
    }
  }

  protected String urlWithLogin(String path, String employeeSign) {
    return urlFor(path, employeeSign);
  }

  /**
   * Runs the action and returns once the answer to the request it triggers on {@code path} is
   * complete. A save whose result only shows after navigating away would otherwise be cut off by
   * that navigation, and a fixed wait is too short on a busy runner and wasted time everywhere else.
   * What the answer then changes on the page is left to the retrying assertions.
   */
  protected Response afterResponse(Page page, String path, Runnable action) {
    Response response = page.waitForResponse(r -> r.url().contains(path), action);
    response.finished();
    return response;
  }

  /**
   * Opens a TomSelect-enhanced {@code <select>} (see AGENTS.md "TomSelect Dropdowns") by
   * clicking its rendered control and picking the option whose visible text contains
   * {@code optionText}, mirroring real user interaction rather than setting the hidden native
   * {@code <select>} value directly.
   */
  protected void selectTomSelectOption(Page page, String selectId, String optionText) {
    Locator control = page.locator("#" + selectId + " ~ .ts-wrapper .ts-control");
    control.click();
    page.locator("#" + selectId + " ~ .ts-wrapper .ts-dropdown .option")
        .filter(new Locator.FilterOptions().setHasText(optionText))
        .first()
        .click();
  }

  /**
   * The shared confirmation dialog (#1032, ADR-0027) once it is on the screen. Every confirmation
   * in the application goes through this one element — there is no native {@code confirm()} left,
   * so {@code page.onDialog(...)} answers nothing any more.
   */
  protected Locator confirmDialog(Page page) {
    Locator dialog = page.locator("#confirmModal");
    dialog.waitFor();
    return dialog;
  }

  /** Answers the confirmation with "yes" and waits for the dialog to be gone again. */
  protected void confirmAction(Page page) {
    confirmDialog(page).locator("#confirmModalAccept").click();
    page.locator("#confirmModal").waitFor(
        new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
  }

  /** Answers the confirmation with "no" — the action must not have happened afterwards. */
  protected void cancelAction(Page page) {
    confirmDialog(page).locator("[data-bs-dismiss=modal]").click();
    page.locator("#confirmModal").waitFor(
        new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
  }

  /**
   * Records every submit that reaches {@code document} after the handlers of salat.js — registered
   * later, the recorder runs last — and whether one of them prevented it, that is whether it would
   * have gone out. Then it prevents the submit itself: the page stays, as it does while the answer
   * is still on its way. A real navigation cannot be held for that: Playwright waits for it to finish
   * before it evaluates or asserts anything on the page again. Read the record with
   * {@link #submits(Page)}.
   */
  protected static void recordSubmits(Page page) {
    page.evaluate("() => { window.e2eSubmits = [];"
        + " document.addEventListener('submit', event => {"
        + " window.e2eSubmits.push(event.defaultPrevented); event.preventDefault(); }); }");
  }

  /** What {@link #recordSubmits(Page)} noted so far: per submit, whether salat.js prevented it. */
  @SuppressWarnings("unchecked")
  protected static List<Boolean> submits(Page page) {
    return (List<Boolean>) page.evaluate("() => window.e2eSubmits");
  }

}
