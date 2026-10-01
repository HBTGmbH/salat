package de.hbt.salat.e2e.dailyreport;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static de.hbt.salat.e2e.E2ETestData.ABSENCE_FREE_DAY;
import static de.hbt.salat.e2e.E2ETestData.ABSENCE_FULLY_BOOKED_DAY;
import static de.hbt.salat.e2e.E2ETestData.ABSENCE_PARTLY_BOOKED_DAY;
import static de.hbt.salat.e2e.E2ETestData.ABSENCE_WEEKEND_DAY;
import static de.hbt.salat.e2e.E2ETestData.EMPLOYEE_ABSENCE_SIGN;
import static de.hbt.salat.e2e.E2ETestData.SUBORDER_ALPHA_DEV_SIGN;
import static de.hbt.salat.e2e.E2ETestData.SUBORDER_KRANKHEIT_SIGN;
import static de.hbt.salat.e2e.E2ETestData.SUBORDER_STANDBY_SIGN;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import de.hbt.salat.e2e.E2EBrowser;
import de.hbt.salat.e2e.PlaywrightE2ETestBase;

/**
 * Wer in der Buchungsmaske eine Abwesenheit wählt — Krankheit, Urlaub und dergleichen —, bekommt
 * den Rest des Tagessolls als Dauer eingetragen (#1214), solange das Feld unberührt ist: leer oder
 * noch mit dem Wert einer früheren Vorbelegung. Die Restzeit selbst rechnet der Server
 * ({@code DailyServiceRemainingDayTargetTest}); hier geht es darum, wann das Formular sie einträgt.
 * Keiner der Tests speichert, der geteilte Zustand der E2E-Datenbank bleibt also unberührt.
 */
class AbsenceDurationPrefillE2ETest extends PlaywrightE2ETestBase {

  private static final String NEW_BOOKING = "/dailyreport/timereports/new";

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void an_absence_on_a_day_without_bookings_gets_the_whole_target(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE_ABSENCE_SIGN, NEW_BOOKING, page -> {
      openOnProject(page, ABSENCE_FREE_DAY);

      selectTomSelectOption(page, "suborderId", SUBORDER_KRANKHEIT_SIGN);

      assertThat(duration(page)).hasValue("08:00");
    });
  }

  /** Drei Stunden Projekt und zwei Stunden Rufbereitschaft: die Bereitschaft ist keine Arbeitszeit (#463). */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void an_absence_on_a_partly_booked_day_gets_what_is_left(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE_ABSENCE_SIGN, NEW_BOOKING, page -> {
      openOnProject(page, ABSENCE_PARTLY_BOOKED_DAY);

      selectTomSelectOption(page, "suborderId", SUBORDER_KRANKHEIT_SIGN);

      assertThat(duration(page)).hasValue("05:00");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void an_absence_on_a_day_booked_to_the_target_gets_nothing(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE_ABSENCE_SIGN, NEW_BOOKING, page -> {
      openOnProject(page, ABSENCE_FULLY_BOOKED_DAY);

      selectTomSelectOption(page, "suborderId", SUBORDER_KRANKHEIT_SIGN);

      assertThat(duration(page)).hasValue("");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_typed_duration_stays_also_on_the_next_change_of_the_suborder(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE_ABSENCE_SIGN, NEW_BOOKING, page -> {
      openOnProject(page, ABSENCE_FREE_DAY);
      duration(page).fill("2:00");

      selectTomSelectOption(page, "suborderId", SUBORDER_KRANKHEIT_SIGN);
      assertThat(duration(page)).hasValue("02:00");

      selectTomSelectOption(page, "suborderId", SUBORDER_ALPHA_DEV_SIGN);
      selectTomSelectOption(page, "suborderId", SUBORDER_KRANKHEIT_SIGN);
      assertThat(duration(page)).hasValue("02:00");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_project_or_standby_keeps_the_proposal_and_proposes_nothing_itself(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE_ABSENCE_SIGN, NEW_BOOKING, page -> {
      openOnProject(page, ABSENCE_FREE_DAY);
      selectTomSelectOption(page, "suborderId", SUBORDER_STANDBY_SIGN);
      assertThat(duration(page)).hasValue("");

      selectTomSelectOption(page, "suborderId", SUBORDER_KRANKHEIT_SIGN);
      selectTomSelectOption(page, "suborderId", SUBORDER_ALPHA_DEV_SIGN);

      assertThat(duration(page)).hasValue("08:00");
    });
  }

  /** Ohne Soll am Samstag (#857) nimmt der neue Tag die noch unberührte Vorbelegung zurück. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_new_date_brings_the_rest_of_that_day_into_an_untouched_field(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE_ABSENCE_SIGN, NEW_BOOKING, page -> {
      openOnProject(page, ABSENCE_FREE_DAY);
      selectTomSelectOption(page, "suborderId", SUBORDER_KRANKHEIT_SIGN);
      assertThat(duration(page)).hasValue("08:00");

      changeDate(page, ABSENCE_PARTLY_BOOKED_DAY);
      assertThat(duration(page)).hasValue("05:00");

      changeDate(page, ABSENCE_WEEKEND_DAY);
      assertThat(duration(page)).hasValue("");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_new_date_leaves_a_typed_duration_alone(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE_ABSENCE_SIGN, NEW_BOOKING, page -> {
      openOnProject(page, ABSENCE_FREE_DAY);
      selectTomSelectOption(page, "suborderId", SUBORDER_KRANKHEIT_SIGN);
      duration(page).fill("6:30");

      changeDate(page, ABSENCE_PARTLY_BOOKED_DAY);

      assertThat(duration(page)).hasValue("06:30");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void in_begin_end_mode_the_end_is_the_begin_plus_the_rest(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE_ABSENCE_SIGN, NEW_BOOKING, page -> {
      openOnProject(page, ABSENCE_PARTLY_BOOKED_DAY);
      page.locator("#btnBeginEnd").click();
      page.locator("#beginTimeInput").fill("08:00");

      selectTomSelectOption(page, "suborderId", SUBORDER_KRANKHEIT_SIGN);

      assertThat(page.locator("#endTimeInput")).hasValue("13:00");
    });
  }

  /** Favorit, Deeplink und Befehlspalette öffnen das Formular alle über den Unterauftrag in der Adresse. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void a_form_opened_on_an_absence_gets_the_rest_unless_it_was_handed_a_duration(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE_ABSENCE_SIGN, NEW_BOOKING, page -> {
      String sick = suborderIdOf(page, SUBORDER_KRANKHEIT_SIGN);

      page.navigate(urlWithLogin(NEW_BOOKING + "?date=" + ABSENCE_PARTLY_BOOKED_DAY + "&suborderId=" + sick,
          EMPLOYEE_ABSENCE_SIGN));
      assertThat(duration(page)).hasValue("05:00");

      page.navigate(urlWithLogin(NEW_BOOKING + "?date=" + ABSENCE_PARTLY_BOOKED_DAY + "&suborderId=" + sick
          + "&duration=1:00", EMPLOYEE_ABSENCE_SIGN));
      assertThat(duration(page)).hasValue("1:00");
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("de.hbt.salat.e2e.PlaywrightE2ETestBase#browsers")
  void editing_a_booking_keeps_its_duration_on_a_change_to_an_absence(E2EBrowser browser) {
    runAsUser(browser, EMPLOYEE_ABSENCE_SIGN,
        "/dailyreport/daily?mode=daily&date=" + ABSENCE_PARTLY_BOOKED_DAY, page -> {
      page.locator("a[href*='/edit']").first().click();
      page.waitForLoadState();
      String stored = duration(page).inputValue();

      selectTomSelectOption(page, "suborderId", SUBORDER_KRANKHEIT_SIGN);

      assertThat(duration(page)).hasValue(stored);
      assertThat(page.locator("#suborderId[data-absence-minutes]")).hasCount(0);
    });
  }

  private Locator duration(Page page) {
    return page.locator("#durationTime");
  }

  private void openOnProject(Page page, LocalDate date) {
    String project = suborderIdOf(page, SUBORDER_ALPHA_DEV_SIGN);
    page.navigate(urlWithLogin(NEW_BOOKING + "?date=" + date + "&suborderId=" + project, EMPLOYEE_ABSENCE_SIGN));
    assertThat(duration(page)).hasValue("");
  }

  private void changeDate(Page page, LocalDate date) {
    afterResponse(page, "/refresh-orders", () -> page.locator("#referenceday").fill(date.toString()));
  }

  /** Reads the option value of a suborder from the rendered dropdown, keyed by its sign. */
  private String suborderIdOf(Page page, String sign) {
    Object value = page.evaluate(
        "sign => Array.from(document.querySelectorAll('#suborderId option'))"
            + ".find(o => o.textContent.includes(sign)).value", sign);
    return String.valueOf(value);
  }
}
