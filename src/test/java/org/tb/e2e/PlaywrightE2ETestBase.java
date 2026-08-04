package org.tb.e2e;

import static org.mockito.Mockito.when;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.time.LocalDateTime;
import java.util.Arrays;
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
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.test.FixedClock;
import org.tb.common.util.ClockProvider;
import org.tb.customer.persistence.CustomerRepository;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.employee.persistence.EmployeecontractRepository;
import org.tb.employee.persistence.VacationRepository;
import org.tb.order.persistence.CustomerorderRepository;
import org.tb.order.persistence.EmployeeorderRepository;
import org.tb.order.persistence.SuborderRepository;

/**
 * Base class for Playwright-driven E2E tests against the {@code dailyreport} module.
 *
 * <p>Starts the real Spring Boot application ({@code webEnvironment = RANDOM_PORT}) with the
 * H2 test datasource ({@code unittest} profile) and the pre-authenticated dev login
 * ({@code local} profile, see {@link org.tb.auth.configuration.LocalDevSecurityConfiguration}).
 * Master data (customers, orders, employees, ...) is seeded once per test run via
 * {@link E2ETestData}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "management.health.mail.enabled=false")
@ActiveProfiles({"unittest", "local"})
@FixedClock(PlaywrightE2ETestBase.FIXED_NOW)
@TestInstance(Lifecycle.PER_CLASS)
public abstract class PlaywrightE2ETestBase {

  static final String FIXED_NOW = "2026-06-15T09:00:00";

  /**
   * Browsers to run each {@code @ParameterizedTest} against. Defaults to every
   * {@link E2EBrowser} (local full-coverage runs); a CI matrix leg narrows this to a single
   * browser via {@code -De2e.browsers=chrome} (or {@code firefox}) so each browser gets its
   * own job/report without duplicating test code.
   */
  static Stream<E2EBrowser> browsers() {
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
  private VacationRepository vacationRepository;

  // no SMTP server is available in the E2E environment; release/acceptance/sharing flows send
  // mail as a side effect, so the sender is stubbed out rather than left to fail with a raw
  // RuntimeException (see MailService.sendEmail)
  @MockitoBean(reset = MockReset.NONE)
  private JavaMailSender mailSender;

  private Playwright playwright;

  @BeforeAll
  void startPlaywrightAndSeedData() {
    when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

    playwright = Playwright.create();
    // the seed computes the Urlaub-suborder sign from "today", so it must run under the same
    // fixed clock the per-test @FixedClock extension applies for assertions
    ClockProvider.useFixedClock(LocalDateTime.parse(FIXED_NOW));
    E2ETestData.seedIfNeeded(customerRepository, customerorderRepository, suborderRepository,
        employeeRepository, employeecontractRepository, employeeorderRepository, salatUserRepository,
        vacationRepository);
  }

  @AfterAll
  void stopPlaywright() {
    playwright.close();
  }

  private String urlFor(String path, String employeeSign) {
    String separator = path.contains("?") ? "&" : "?";
    return "http://localhost:" + port + path + separator + "login-name=" + employeeSign;
  }

  /**
   * Launches the given browser, logs in as {@code employeeSign} by navigating to
   * {@code startPath} with {@code ?login-name=...}, and runs {@code testBody} against the
   * resulting page. Every subsequent {@code page.navigate(...)} call in the test body should
   * keep appending {@code login-name} (see {@link #urlFor}) rather than relying solely on the
   * {@code salat_dev_login} cookie, since it is marked {@code Secure} and cookie persistence
   * over plain {@code http://localhost} is browser-dependent.
   */
  protected void runAsUser(E2EBrowser browser, String employeeSign, String startPath, Consumer<Page> testBody) {
    try (Browser b = browser.launch(playwright)) {
      // pin the locale so assertions on rendered (German) text are deterministic regardless of
      // the browser's own default Accept-Language
      BrowserContext context = b.newContext(new Browser.NewContextOptions().setLocale("de-DE"));
      Page page = context.newPage();
      page.navigate(urlFor(startPath, employeeSign));
      testBody.accept(page);
    }
  }

  protected String urlWithLogin(String path, String employeeSign) {
    return urlFor(path, employeeSign);
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

}
