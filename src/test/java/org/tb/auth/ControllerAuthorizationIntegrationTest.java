package org.tb.auth;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.FOUND;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BO;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_MA;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_PV;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_RESTRICTED;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.employee.persistence.EmployeecontractRepository;
import org.tb.etl.persistence.ETLRunHistoryRepository;

/**
 * #926: Die Controller tragen ihre Rechteprüfung als {@code @Authorized(requires…)} statt als
 * {@code @PreAuthorize("hasRole(…)")}. Die Annotation ist die Absicht; ob sie greift, entscheidet
 * sich erst im Zusammenspiel von Sicherheitskette, {@code AuthorizationAspect} und
 * Fehlerbehandlung — und genau dort lag der Unterschied: die Ausnahme des Aspekts kam als
 * {@code 500} heraus, bis {@code AuthorizationExceptionHandler} sie beantwortete. Deshalb ein Test
 * über einen echten Port und nicht über die Annotationen.
 *
 * <p>Geprüft wird der direkte Aufruf der URL, nicht das Menü. Ein ausgeblendeter Menüeintrag ist
 * keine Autorisierung — das war die Lücke aus #919.
 *
 * <p>Jede Anmeldung trägt einen heute gültigen Vertrag. Ohne ihn antwortete schon
 * {@code EmployeeAccessFilter} mit 403 (#1054) und jede Zeile wäre aus dem falschen Grund grün.
 *
 * <p>Eigene H2-Datenbank: die übrigen {@code @SpringBootTest}-Klassen teilen sich eine, und ein
 * zweiter Anwendungskontext mit {@code ddl-auto: create} legte sie beim Hochfahren neu an.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-926;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR",
    "management.health.mail.enabled=false"
})
@ActiveProfiles({"unittest", "local"})
@DisplayNameGeneration(ReplaceUnderscores.class)
class ControllerAuthorizationIntegrationTest {

  private static final String RESTRICTED = "res";
  private static final String REGULAR = "reg";
  private static final String BACKOFFICE = "bof";
  private static final String PEOPLE_LEAD = "ple";
  private static final String MANAGER = "mgr";

  private static final List<String> ALL_LOGINS =
      List.of(RESTRICTED, REGULAR, BACKOFFICE, PEOPLE_LEAD, MANAGER);

  /** Stammdatensichten: alles ausser {@code RESTRICTED}. */
  private static final List<String> UNRESTRICTED_VIEWS = List.of(
      "/customers",
      "/employees",
      "/employees/contracts",
      "/orders/customerorders",
      "/orders/suborders",
      "/orders/employeeorders");

  /**
   * Pflege der Stammdaten und alles, was Zeiten verschiebt. Dazu die Berechtigungsregeln selbst
   * (#1074): wer sie schreiben darf, kann sich jedes Recht daraus selbst gewähren — und anders als
   * die ETL-Laufhistorie steht diese Seite deshalb ausdrücklich <em>nicht</em> auch denen offen,
   * die eine Regel tragen.
   */
  private static final List<String> MANAGER_VIEWS = List.of(
      "/customers/create",
      "/employees/create",
      "/employees/contracts/create",
      "/orders/customerorders/create",
      "/dailyreports/move",
      "/auth/rules",
      "/auth/rules/create");

  /**
   * Der Betrieb der Anwendung. Hier entscheidet nicht die Rolle allein: eine Regel der Kategorie
   * {@code ETL} mit {@code EXECUTE} kommt ebenfalls durch (#573). Keine der Anmeldungen hier trägt
   * eine solche Regel, deshalb steht die Liste neben den Geschäftsführungssichten.
   */
  private static final List<String> ETL_VIEWS = List.of(
      "/etl/runs");

  /**
   * Das Anstoßen eines Laufs (#1071) zieht dieselbe Linie wie die Liste — deshalb steht es hier und
   * nicht bei den Geschäftsführungssichten. Ein POST ist an dieser Stelle prüfbar, weil der
   * {@code local}-Profilkette der CSRF-Schutz bewusst fehlt (→ ADR-0018).
   */
  private static final String ETL_RUN_ACTION = "/etl/runs/run";

  /** Rechnungen und die Umsätze aus Buchhaltung und Aufzeichnungen. */
  private static final List<String> BACKOFFICE_VIEWS = List.of(
      "/invoice",
      "/orders/revenue-upload");

  /** Abnahme und die geplanten Berichte. */
  private static final List<String> PEOPLE_LEAD_VIEWS = List.of(
      "/acceptance",
      "/reporting/jobs");

  /** Platzhalter für die id des Vertrags einer Anmeldung, etwa {@code {reg}}. */
  private static final Pattern CONTRACT_OF = Pattern.compile("\\{([a-z]+)}");

  /**
   * Der Monat, den die Übersicht vor der Freigabe zeigt (#760). Die Verträge beginnen am
   * 01.01.2000 und sind nie freigegeben, der Zeitraum ist also der ganze Januar 2000 — ohne eine
   * einzige Buchung: jeder Arbeitstag ist ein Befund, die Bilanz hat ein Soll, und beide Sichten
   * haben zu zeigen.
   */
  private static final String REVIEW_MONTH = "until=2000-01";

  @LocalServerPort
  private int port;
  @Autowired
  private EmployeeRepository employeeRepository;
  @Autowired
  private EmployeecontractRepository employeecontractRepository;
  @Autowired
  private SalatUserRepository salatUserRepository;
  @Autowired
  private ETLRunHistoryRepository etlRunHistoryRepository;

  @BeforeEach
  void seedOneLoginPerRole() {
    employeeWithContract(RESTRICTED, EMPLOYEE_STATUS_RESTRICTED);
    employeeWithContract(REGULAR, EMPLOYEE_STATUS_MA);
    employeeWithContract(BACKOFFICE, EMPLOYEE_STATUS_BO);
    employeeWithContract(PEOPLE_LEAD, EMPLOYEE_STATUS_PV);
    employeeWithContract(MANAGER, EMPLOYEE_STATUS_BL);
  }

  @ParameterizedTest(name = "{1} -> {0}")
  @MethodSource("allowed")
  void an_authorized_login_reaches_the_view(String path, String login) throws Exception {
    assertThat(get(path, login).statusCode()).isEqualTo(200);
  }

  @ParameterizedTest(name = "{1} -> {0}")
  @MethodSource("denied")
  void an_unauthorized_login_is_turned_away_with_403(String path, String login) throws Exception {
    assertThat(get(path, login).statusCode()).isEqualTo(FORBIDDEN.value());
  }

  /**
   * Eine abgewiesene Anfrage endet auf der Fehlerseite der Anwendung, nicht auf der des
   * Servlet-Containers. Genau das war vor {@code AuthorizationExceptionHandler} nicht der Fall: die
   * Ausnahme des Aspekts kam als {@code 500} heraus.
   */
  @ParameterizedTest(name = "{1} -> {0}")
  @MethodSource("oneDenialPerLevel")
  void a_denial_ends_on_the_error_page_of_the_application(String path, String login) throws Exception {
    // copyErrorDetails steht nur in error/error.html und unterscheidet die Fehlerseite der
    // Anwendung von der des Servlet-Containers, unabhängig von der Sprache.
    assertThat(get(path, login).body()).contains("copyErrorDetails");
  }

  @ParameterizedTest(name = "{0} -> POST " + ETL_RUN_ACTION)
  @MethodSource("etlDenied")
  void an_unauthorized_login_cannot_start_an_etl_run(String login) throws Exception {
    // Ein ausgeblendetes Formular ist keine Autorisierung — geprueft wird der abgeschickte POST.
    // Der Zeitraum liegt richtig herum: nur dann kommt die Anfrage ueberhaupt bis zur
    // Rechtepruefung, die hier die Aussage ist.
    assertThat(post(ETL_RUN_ACTION, login, "dateFrom=2026-01-01&dateUntil=2026-01-31").statusCode())
        .as("eine Anmeldung ohne Regel ETL/EXECUTE bekommt 403 und nicht still eine Umleitung")
        .isEqualTo(FORBIDDEN.value());
    assertThat(etlRunHistoryRepository.count()).isZero();
  }

  @Test
  void an_authorized_login_gets_past_the_permission_check_without_starting_a_run() throws Exception {
    // Von nach Bis: die Eingabe wird abgewiesen, bevor ein Lauf entsteht. Die Anmeldung darf, die
    // Eingabe nicht — deshalb keine 403, sondern die Umleitung zurueck auf die Liste mit der
    // Meldung. Ein echter Lauf wuerde hier gegen die Testdatenbank arbeiten, und der Test pruefte
    // nicht mehr die Berechtigung, sondern den ETL.
    //
    // Dass diese Zeile ueberhaupt etwas aussagt, haengt an der Reihenfolge in
    // ETLService.resolveManualRun: dort faellt die Berechtigung VOR der Eingabepruefung. Andersherum
    // bekaeme auch eine Anmeldung ohne jede ETL-Regel hier eine 302, und der Test waere gruen, ohne
    // etwas zu zeigen.
    var response = post(ETL_RUN_ACTION, MANAGER, "dateFrom=2026-01-31&dateUntil=2026-01-01");

    assertThat(response.statusCode()).isEqualTo(FOUND.value());
    assertThat(etlRunHistoryRepository.count()).isZero();
  }

  private static Stream<String> etlDenied() {
    return Stream.of(RESTRICTED, REGULAR, BACKOFFICE, PEOPLE_LEAD);
  }

  private static Stream<Arguments> allowed() {
    return Stream.of(
        pairs(UNRESTRICTED_VIEWS, REGULAR, BACKOFFICE, PEOPLE_LEAD, MANAGER),
        pairs(MANAGER_VIEWS, MANAGER),
        pairs(ETL_VIEWS, MANAGER),
        pairs(BACKOFFICE_VIEWS, BACKOFFICE, MANAGER),
        pairs(PEOPLE_LEAD_VIEWS, PEOPLE_LEAD, MANAGER)
    ).flatMap(s -> s);
  }

  private static Stream<Arguments> denied() {
    return Stream.of(
        pairs(UNRESTRICTED_VIEWS, RESTRICTED),
        pairs(MANAGER_VIEWS, RESTRICTED, REGULAR, BACKOFFICE, PEOPLE_LEAD),
        pairs(ETL_VIEWS, RESTRICTED, REGULAR, BACKOFFICE, PEOPLE_LEAD),
        pairs(BACKOFFICE_VIEWS, RESTRICTED, REGULAR, PEOPLE_LEAD),
        pairs(PEOPLE_LEAD_VIEWS, RESTRICTED, REGULAR, BACKOFFICE)
    ).flatMap(s -> s);
  }

  /** Je eine Stichprobe pro Berechtigungsstufe. */
  private static Stream<Arguments> oneDenialPerLevel() {
    return Stream.of(
        Arguments.of("/customers", RESTRICTED),
        Arguments.of("/customers/create", REGULAR),
        Arguments.of("/invoice", REGULAR),
        Arguments.of("/acceptance", REGULAR),
        // die Ablehnung kommt hier aus dem Service, nicht aus dem Aspekt des Controllers
        Arguments.of("/release/review?" + REVIEW_MONTH + "&fEmployeeContractId={mgr}", REGULAR),
        Arguments.of("/acceptance/accept/review?contractId={reg}&" + REVIEW_MONTH, PEOPLE_LEAD));
  }

  /**
   * Die Übersicht vor der Freigabe (#760) sieht, wer den Vertrag freigeben darf: die Person selbst —
   * auch eine eingeschränkte, sie gibt ihre eigenen Stunden frei —, die Geschäftsführung und die
   * zuständige People Lead. Eine 200 heißt dabei zugleich, dass die Seite mit allen ihren Bausteinen
   * gerendert wurde, in beiden Sichten.
   */
  @ParameterizedTest(name = "{1} -> {0}")
  @MethodSource("reviewAllowed")
  void a_login_that_may_release_the_contract_sees_its_review(String path, String login) throws Exception {
    var response = get(path, login);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("id=\"review-findings\"", "id=\"review-action\"");
  }

  /**
   * Die gesperrte Übersicht (#760): ein Befund über den ganzen Zeitraum, keine Sichten und kein
   * Formular zum Freigeben. Auch sie muss mit allen Bausteinen rendern — die Tests des Service und
   * des ViewHelpers rendern kein Template. Der Dezember 1999 liegt vor dem Vertragsbeginn; ein Monat
   * in ferner Zukunft liegt hinter der Grenze der Freigabe, und die Seite steht, ohne die Tage bis
   * dorthin zu bestimmen.
   */
  @ParameterizedTest(name = "{1} -> {0}")
  @MethodSource("reviewBlocked")
  void a_blocked_review_shows_its_period_finding_without_the_release_form(String path, String login)
      throws Exception {
    assertThatTheReviewIsBlocked(get(path, login));
  }

  /**
   * Ein Monat, der schon freigegeben ist, war bis #760 eine {@code IllegalArgumentException} auf der
   * Fehlerseite. Jetzt ist er der Befund {@code RL-0009} der gesperrten Übersicht.
   */
  @Test
  void a_month_already_released_is_a_blocked_review() throws Exception {
    var contract = contractOf(BACKOFFICE);
    contract.setReportReleaseDate(LocalDate.of(2000, 1, 31));
    employeecontractRepository.save(contract);
    try {
      var response = get("/release/review?" + REVIEW_MONTH, BACKOFFICE);

      assertThatTheReviewIsBlocked(response);
      assertThat(response.body()).containsAnyOf(
          "Bis zum gewählten Monat ist bereits alles freigegeben.",
          "Everything up to the selected month has already been released.");
    } finally {
      var released = contractOf(BACKOFFICE);
      released.setReportReleaseDate(null);
      employeecontractRepository.save(released);
    }
  }

  private static void assertThatTheReviewIsBlocked(HttpResponse<String> response) {
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("id=\"review-period-errors\"", "id=\"review-action\"");
    assertThat(response.body()).doesNotContain("name=\"periodBegin\"", "id=\"review-views\"", "id=\"review-findings\"");
  }

  /**
   * Wer den Vertrag nicht freigeben darf, sieht auch seine Übersicht nicht (#760) — über die eigene
   * Freigabe so wenig wie über die Abnahme. Den Vertrag nennt die Anfrage selbst; die Prüfung liegt
   * deshalb im Service, bei der Abnahme zusätzlich am Controller.
   */
  @ParameterizedTest(name = "{1} -> {0}")
  @MethodSource("reviewDenied")
  void a_login_that_may_not_release_the_contract_does_not_see_its_review(String path, String login)
      throws Exception {
    assertThat(get(path, login).statusCode()).isEqualTo(FORBIDDEN.value());
  }

  /**
   * Das Freigeben selbst: die Handler fangen nur fachliche Ausnahmen, eine fehlende Berechtigung
   * bleibt eine 403 und wird nicht zum Toast nach einer Umleitung.
   */
  @ParameterizedTest(name = "{1} -> POST {0} for {2}")
  @MethodSource("releaseDenied")
  void a_login_that_may_not_release_the_contract_cannot_release_it(String path, String login, String owner)
      throws Exception {
    var form = "contractId={" + owner + "}&periodBegin=2000-01-01&periodEnd=2000-01-31";

    assertThat(post(path, login, form).statusCode()).isEqualTo(FORBIDDEN.value());
    assertThat(contractOf(owner).getReportReleaseDate()).isNull();
  }

  /**
   * Ein Tag der Übersicht führt in die Tagesansicht und von dort zurück (#760). Auch das prüft den
   * gerenderten Weg: eine 200 mit dem Verweis heißt, dass die Tagesansicht ihn gebaut hat. Eine
   * Buchung, die dort angelegt wird, kehrt in den Tag zurück und behält den Weg in die Übersicht —
   * die Adresse der Übersicht steckt verschachtelt in der des Tages.
   */
  @Test
  void a_day_opened_from_the_review_leads_back_to_it() throws Exception {
    var review = "/release/review?" + REVIEW_MONTH + "&view=day#day-2000-01-03";
    var response = get("/dailyreport/daily?mode=daily&date=2000-01-03"
        + "&returnUrl=%2Frelease%2Freview%3F" + REVIEW_MONTH.replace("=", "%3D") + "%26view%3Dday%23day-2000-01-03",
        REGULAR);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("href=\"/release/review?" + REVIEW_MONTH + "&amp;view=day#day-2000-01-03\"");

    var newBooking = Pattern.compile("href=\"(/dailyreport/timereports/new\\?[^\"]*)\"").matcher(response.body());
    assertThat(newBooking.find()).as("the day offers to create a booking").isTrue();
    var day = queryParam(newBooking.group(1).replace("&amp;", "&"), "returnUrl");
    assertThat(day).startsWith("/dailyreport/daily?mode=daily&date=2000-01-03&returnUrl=");
    assertThat(queryParam(day, "returnUrl")).isEqualTo(review);
  }

  /**
   * Die Übersicht vor der Abnahme (#1122) über einen freigegebenen Monat: Bilanz, Sichten und das
   * Formular zum Abnehmen, aber kein Anlegen an einem Tag ohne Buchung. Eine 200 heißt auch hier,
   * dass die Seite mit allen Bausteinen gerendert wurde, in beiden Sichten.
   */
  @ParameterizedTest(name = "view [{0}]")
  @ValueSource(strings = {"", "&view=day"})
  void a_manager_sees_the_acceptance_review_of_a_released_month(String view) throws Exception {
    releaseJanuary(REGULAR);
    try {
      var response = get("/acceptance/accept/review?contractId={reg}&" + REVIEW_MONTH + view, MANAGER);

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("id=\"review-summary\"", "id=\"review-views\"", "id=\"review-action\"",
          "action=\"/acceptance/accept\"", "name=\"periodBegin\" value=\"2000-01-01\"",
          "name=\"periodEnd\" value=\"2000-01-31\"");
      assertThat(response.body()).doesNotContain("id=\"review-period-errors\"", "id=\"review-findings\"",
          "/dailyreport/timereports/new?date=");
    } finally {
      unrelease(REGULAR);
    }
  }

  /**
   * Ohne Freigabe gibt es nichts abzunehmen (#1122): die gesperrte Übersicht mit ihrem Befund, ohne
   * Sichten und ohne Formular.
   */
  @Test
  void the_acceptance_review_of_a_contract_never_released_is_blocked() throws Exception {
    var response = get("/acceptance/accept/review?contractId={reg}&" + REVIEW_MONTH, MANAGER);

    assertThatTheReviewIsBlocked(response);
    assertThat(response.body()).containsAnyOf(
        "Es ist noch nichts freigegeben, was abgenommen werden könnte.",
        "Nothing has been released yet that could be accepted.");
  }

  /**
   * Wer den Vertrag nicht abnehmen darf, sieht auch seine Übersicht nicht (#1122): wer keine People
   * Lead ist, scheitert am Controller, die People Lead, die nicht zuständig ist, am Service — und
   * die eigenen Buchungen nimmt niemand ab, auch die Geschäftsführung nicht.
   */
  @ParameterizedTest(name = "{1} -> {0}")
  @MethodSource("acceptanceReviewDenied")
  void a_login_that_may_not_accept_the_contract_does_not_see_its_review(String path, String login)
      throws Exception {
    releaseJanuary(REGULAR);
    releaseJanuary(MANAGER);
    try {
      assertThat(get(path, login).statusCode()).isEqualTo(FORBIDDEN.value());
    } finally {
      unrelease(REGULAR);
      unrelease(MANAGER);
    }
  }

  /**
   * Das Abnehmen selbst: auch hier fängt der Handler nur fachliche Ausnahmen, eine fehlende
   * Berechtigung bleibt eine 403, und das Abnahmedatum bleibt stehen.
   */
  @ParameterizedTest(name = "{0} -> POST /acceptance/accept for {1}")
  @MethodSource("acceptDenied")
  void a_login_that_may_not_accept_the_contract_cannot_accept_it(String login, String owner) throws Exception {
    releaseJanuary(owner);
    try {
      var form = "contractId={" + owner + "}&periodBegin=2000-01-01&periodEnd=2000-01-31";

      assertThat(post("/acceptance/accept", login, form).statusCode()).isEqualTo(FORBIDDEN.value());
      assertThat(contractOf(owner).getReportAcceptanceDate()).isNull();
    } finally {
      unrelease(owner);
    }
  }

  /** Den eigenen Vertrag bietet die Seite der Abnahme nicht zum Abnehmen an, sondern sagt, warum (#1122). */
  @Test
  void the_acceptance_page_offers_no_review_of_the_own_contract() throws Exception {
    var own = get("/acceptance?fAcceptanceEmployeeContractId={mgr}", MANAGER);
    var other = get("/acceptance?fAcceptanceEmployeeContractId={reg}", MANAGER);

    assertThat(own.statusCode()).isEqualTo(200);
    assertThat(own.body()).contains("id=\"acceptance-accept-not-allowed\"").doesNotContain("id=\"acceptance-accept-until\"");
    assertThat(other.statusCode()).isEqualTo(200);
    assertThat(other.body()).contains("id=\"acceptance-accept-until\"").doesNotContain("id=\"acceptance-accept-not-allowed\"");
  }

  private static Stream<Arguments> acceptanceReviewDenied() {
    return Stream.of(
        Arguments.of("/acceptance/accept/review?contractId={mgr}&" + REVIEW_MONTH, REGULAR),
        Arguments.of("/acceptance/accept/review?contractId={reg}&" + REVIEW_MONTH, RESTRICTED),
        Arguments.of("/acceptance/accept/review?contractId={reg}&" + REVIEW_MONTH, PEOPLE_LEAD),
        Arguments.of("/acceptance/accept/review?contractId={mgr}&" + REVIEW_MONTH, MANAGER));
  }

  private static Stream<Arguments> acceptDenied() {
    return Stream.of(
        Arguments.of(REGULAR, REGULAR),
        Arguments.of(PEOPLE_LEAD, REGULAR),
        Arguments.of(MANAGER, MANAGER));
  }

  /** Gibt den Januar 2000 des Vertrags frei, wie es die Freigabe täte — ohne Buchungen ist nichts umzustellen. */
  private void releaseJanuary(String sign) {
    var contract = contractOf(sign);
    contract.setReportReleaseDate(LocalDate.of(2000, 1, 31));
    employeecontractRepository.save(contract);
  }

  private void unrelease(String sign) {
    var contract = contractOf(sign);
    contract.setReportReleaseDate(null);
    contract.setReportAcceptanceDate(null);
    employeecontractRepository.save(contract);
  }

  /** Ein Parameter einer Adresse, einmal dekodiert — so, wie der Server ihn liest. */
  private static String queryParam(String url, String name) {
    var value = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
    return value != null ? URLDecoder.decode(value, UTF_8) : null;
  }

  private static Stream<Arguments> reviewAllowed() {
    return Stream.of(
        Arguments.of("/release/review?" + REVIEW_MONTH, REGULAR),
        Arguments.of("/release/review?" + REVIEW_MONTH + "&view=day", REGULAR),
        Arguments.of("/release/review?" + REVIEW_MONTH, RESTRICTED),
        Arguments.of("/release/review?" + REVIEW_MONTH + "&fEmployeeContractId={reg}", MANAGER),
        Arguments.of("/release/review?" + REVIEW_MONTH + "&fEmployeeContractId={reg}&view=day", MANAGER),
        Arguments.of("/acceptance/release/review?contractId={reg}&" + REVIEW_MONTH, MANAGER),
        Arguments.of("/acceptance/release/review?contractId={reg}&" + REVIEW_MONTH + "&view=day", MANAGER));
  }

  private static Stream<Arguments> reviewBlocked() {
    return Stream.of(
        Arguments.of("/release/review?until=1999-12", REGULAR),
        Arguments.of("/release/review?until=1999-12&view=day", REGULAR),
        Arguments.of("/acceptance/release/review?contractId={reg}&until=1999-12", MANAGER),
        Arguments.of("/release/review?until=9999-12", REGULAR),
        Arguments.of("/release/review?until=%2B99999-12&view=day", RESTRICTED));
  }

  private static Stream<Arguments> reviewDenied() {
    return Stream.of(
        Arguments.of("/release/review?" + REVIEW_MONTH + "&fEmployeeContractId={mgr}", REGULAR),
        Arguments.of("/release/review?" + REVIEW_MONTH + "&fEmployeeContractId={reg}", RESTRICTED),
        Arguments.of("/release/review?" + REVIEW_MONTH + "&fEmployeeContractId={reg}", PEOPLE_LEAD),
        Arguments.of("/acceptance/release/review?contractId={mgr}&" + REVIEW_MONTH, REGULAR),
        Arguments.of("/acceptance/release/review?contractId={reg}&" + REVIEW_MONTH, PEOPLE_LEAD));
  }

  private static Stream<Arguments> releaseDenied() {
    return Stream.of(
        Arguments.of("/release", REGULAR, MANAGER),
        Arguments.of("/release", RESTRICTED, REGULAR),
        Arguments.of("/acceptance/release", REGULAR, REGULAR),
        Arguments.of("/acceptance/release", PEOPLE_LEAD, REGULAR));
  }

  private static Stream<Arguments> pairs(List<String> paths, String... logins) {
    return paths.stream().flatMap(path -> Stream.of(logins).map(login -> Arguments.of(path, login)));
  }

  private HttpResponse<String> get(String path, String login) throws Exception {
    var uri = URI.create("http://localhost:" + port + withLogin(path, login));
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri).GET().build(), BodyHandlers.ofString());
  }

  /**
   * Umleitungen werden bewusst nicht verfolgt ({@code HttpClient} tut das voreingestellt nicht):
   * die Antwort auf den POST selbst ist die Aussage, nicht die Seite danach.
   */
  private HttpResponse<String> post(String path, String login, String form) throws Exception {
    var uri = URI.create("http://localhost:" + port + withLogin(path, login));
    var request = HttpRequest.newBuilder(uri)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(BodyPublishers.ofString(withContractIds(form)))
        .build();
    return HttpClient.newHttpClient().send(request, BodyHandlers.ofString());
  }

  /**
   * Die Anmeldung reist als Parameter mit — an einen Pfad, der schon eine Abfrage trägt, mit
   * {@code &}. Platzhalter wie {@code {reg}} werden zur id des Vertrags dieser Anmeldung.
   */
  private String withLogin(String path, String login) {
    assertThat(ALL_LOGINS).contains(login);
    return withContractIds(path) + (path.contains("?") ? "&" : "?") + "login-name=" + login;
  }

  private String withContractIds(String text) {
    return CONTRACT_OF.matcher(text).replaceAll(sign -> String.valueOf(contractOf(sign.group(1)).getId()));
  }

  private Employeecontract contractOf(String sign) {
    var employee = employeeRepository.findBySign(sign).orElseThrow();
    return employeecontractRepository.findAllByEmployeeId(employee.getId()).getFirst();
  }

  private void employeeWithContract(String sign, String status) {
    if (employeeRepository.findBySign(sign).isPresent()) {
      return;
    }
    SalatUser salatUser = new SalatUser();
    salatUser.setLoginname(sign);
    salatUser.setStatus(status);
    salatUser = salatUserRepository.save(salatUser);

    Employee employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Vorname");
    employee.setLastname(sign);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setSalatUser(salatUser);
    employee = employeeRepository.save(employee);

    Employeecontract contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setValidFrom(LocalDate.of(2000, 1, 1));
    contract.setDailyWorkingTime(Duration.ofHours(8));
    employeecontractRepository.save(contract);
  }

}
