package de.hbt.salat.reporting.controller;

import static de.hbt.salat.testutils.ReferenceTestUtils.salatUserWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.auth.domain.AccessLevel.EXECUTE;
import static de.hbt.salat.auth.domain.AccessLevel.WRITE;
import static de.hbt.salat.common.palette.PaletteKind.REPORT;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.common.palette.PaletteCommand;
import de.hbt.salat.common.palette.PaletteHit;
import de.hbt.salat.common.palette.PaletteParameter;
import de.hbt.salat.common.palette.PaletteQuery;
import de.hbt.salat.common.palette.PaletteSuggestion;
import de.hbt.salat.common.palette.PaletteSuggestionRequest;
import de.hbt.salat.common.palette.PaletteTarget;
import de.hbt.salat.common.palette.PaletteText;
import de.hbt.salat.reporting.auth.ReportAuthorization;
import de.hbt.salat.reporting.domain.ReportDefinition;
import de.hbt.salat.reporting.persistence.ReportDefinitionRepository;
import de.hbt.salat.reporting.service.ReportGroupNames;
import de.hbt.salat.reporting.service.ReportService;
import de.hbt.salat.employee.service.EmployeeService;

/**
 * What the command palette offers of a report, per role (#1232). The palette offers nothing whose
 * page would answer 403 or 404: a report appears only for whoever may execute it — management and
 * people leads all, everybody else by a rule —, and "edit" only where the edit page would open,
 * which takes the role people lead besides write access.
 *
 * <p>Service and {@link ReportAuthorization} are the real ones, only the repository and the store of
 * the rules are mocked: the role decisions are the subject, and they are made where the report page
 * makes them. The roles are stubbed as {@code EmployeeStatusAuthorities} grants them: a manager is
 * people lead as well.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReportingPaletteProviderTest {

  private static final PaletteQuery QUERY = PaletteQuery.of("umsatz");
  private static final long OWN_USER_ID = 5L;
  private static final long OTHER_USER_ID = 9L;

  private static final ReportDefinition WITH_PARAMETERS = report(7L, "Umsatz je Auftrag",
      "select * from umsatz where monat = :monat and jahr = :jahr or :monat is null", OWN_USER_ID);
  private static final ReportDefinition WITHOUT_PARAMETERS = report(8L, "Umsatzübersicht",
      "select * from umsatz", OTHER_USER_ID);

  @Mock
  private ReportDefinitionRepository reportDefinitionRepository;

  @Mock
  private AuthorizedUser authorizedUser;

  @Mock
  private AuthService authService;

  private ReportingPaletteProvider provider;

  @BeforeEach
  void setUp() {
    var reportAuthorization = new ReportAuthorization(authorizedUser, authService);
    var reportService = new ReportService(reportDefinitionRepository, null, reportAuthorization, authorizedUser, null,
        null, new ReportGroupNames(mock(EmployeeService.class)));
    provider = new ReportingPaletteProvider(reportService, reportAuthorization, authorizedUser);
    when(authorizedUser.getEffectiveUserId()).thenReturn(OWN_USER_ID);
    when(reportDefinitionRepository.findAllWithGroup()).thenReturn(List.of(WITH_PARAMETERS, WITHOUT_PARAMETERS));
  }

  @Test
  void the_management_finds_every_report_and_may_edit_each() {
    actingAsManager();

    var hits = provider.search(QUERY);

    assertThat(hits).extracting(PaletteHit::title).containsExactly("Umsatz je Auftrag", "Umsatzübersicht");
    assertThat(hrefs(hits.get(0))).containsExactly(
        "/reporting/reports/execute?id=7", "/reporting/reports/edit?id=7",
        "/reporting/reports?fReportFilter=Umsatz+je+Auftrag");
    assertThat(hrefs(hits.get(1))).contains("/reporting/reports/edit?id=8");
  }

  @Test
  void a_people_lead_finds_every_report_but_edits_only_their_own() {
    when(authorizedUser.isPeopleLead()).thenReturn(true);

    var hits = provider.search(QUERY);

    assertThat(hits).extracting(PaletteHit::title).containsExactly("Umsatz je Auftrag", "Umsatzübersicht");
    assertThat(hrefs(hits.get(0))).contains("/reporting/reports/edit?id=7");
    assertThat(hrefs(hits.get(1))).containsExactly(
        "/reporting/reports/execute?id=8", "/reporting/reports?fReportFilter=Umsatz%C3%BCbersicht");
  }

  @Test
  void an_employee_finds_only_the_reports_a_rule_lets_them_execute() {
    grant(EXECUTE, "7");

    var hits = provider.search(QUERY);

    assertThat(hits).extracting(PaletteHit::key).containsExactly("7");
  }

  @Test
  void an_employee_without_a_rule_finds_no_report() {
    assertThat(provider.search(QUERY)).isEmpty();
  }

  /** The edit page wants a people lead; a rule granting write access does not open it. */
  @Test
  void an_employee_with_write_access_by_a_rule_is_not_offered_the_edit_page() {
    grant(EXECUTE, "7");
    grant(WRITE, "7");

    var hits = provider.search(QUERY);

    assertThat(hrefs(hits.getFirst())).noneMatch(href -> href.contains("/edit"));
  }

  @Test
  void a_restricted_login_without_a_rule_finds_no_report() {
    when(authorizedUser.isRestricted()).thenReturn(true);

    assertThat(provider.search(QUERY)).isEmpty();
  }

  /** The report page itself lets a restricted login with a rule in; the palette asks the same question. */
  @Test
  void a_restricted_login_finds_what_a_rule_lets_it_execute_as_on_the_report_page() {
    when(authorizedUser.isRestricted()).thenReturn(true);
    grant(EXECUTE, "7");

    assertThat(provider.search(QUERY)).extracting(PaletteHit::key).containsExactly("7");
  }

  @Test
  void the_line_names_the_parameters_in_the_order_of_the_sql_and_no_id() {
    actingAsManager();

    var hits = provider.search(QUERY);

    var withParameters = hits.get(0);
    assertThat(withParameters.kind()).isEqualTo(REPORT);
    assertThat(withParameters.context())
        .isEqualTo(PaletteText.of("main.palette.context.report.parameters", "monat, jahr"));
    assertThat(withParameters.subtitle()).isNull();
    assertThat(withParameters.title()).doesNotContain("7");
    assertThat(hits.get(1).context()).isNull();
  }

  /** The hit brings its parameters along like the suggestion of the command: Tab takes it into it (#1493). */
  @Test
  void the_hit_carries_the_parameters_in_the_order_the_command_asks_for_them() {
    actingAsManager();

    var hits = provider.search(QUERY);

    assertThat(hits.get(0).parameters()).isEqualTo("monat:month,jahr:year");
    assertThat(hits.get(1).parameters()).isEmpty();
  }

  @Test
  void enter_runs_the_report_and_the_targets_are_labelled_as_such() {
    actingAsManager();

    var targets = provider.search(QUERY).getFirst().targets();

    assertThat(targets).extracting(PaletteTarget::label).extracting(PaletteText::key).containsExactly(
        "main.palette.target.report.execute", "main.palette.target.report.edit",
        "main.palette.target.report.list");
    assertThat(targets.getFirst().rank()).isEqualTo(PaletteTarget.OPEN);
  }

  /** A limit before the check could cut away exactly the few reports an employee may run. */
  @Test
  void the_permission_is_checked_before_the_candidates_are_limited() {
    var many = IntStream.rangeClosed(1, 40)
        .mapToObj(id -> report((long) id, "Umsatz " + id, "select 1", OTHER_USER_ID))
        .toList();
    when(reportDefinitionRepository.findAllWithGroup()).thenReturn(many);
    grant(EXECUTE, "40");

    assertThat(provider.search(QUERY)).extracting(PaletteHit::key).containsExactly("40");
  }

  private void actingAsManager() {
    when(authorizedUser.isManager()).thenReturn(true);
    when(authorizedUser.isPeopleLead()).thenReturn(true);
  }

  private void grant(AccessLevel accessLevel, String reportId) {
    when(authService.isAuthorized(eq("REPORT_DEFINITION"), any(), eq(accessLevel), eq(reportId))).thenReturn(true);
  }

  private static List<String> hrefs(PaletteHit hit) {
    return hit.targets().stream().map(PaletteTarget::href).toList();
  }

  private static ReportDefinition report(Long id, String name, String sql, long ownerUserId) {
    var report = new ReportDefinition();
    ReflectionTestUtils.setField(report, "id", id);
    report.setName(name);
    report.setSql(sql);
    report.setOwner(salatUserWithId(ownerUserId));
    return report;
  }

  /** The command {@code report} (#1493): the reports a person may execute, each with its parameters. */
  @Test
  void the_command_report_offers_each_report_with_the_parameters_it_expects() {
    actingAsManager();

    var suggestions = provider.suggest(reportRequest("umsatz"));

    assertThat(suggestions).extracting(PaletteSuggestion::value).containsExactly("7", "8");
    var withParameters = suggestions.getFirst();
    assertThat(withParameters.label()).isEqualTo("Umsatz je Auftrag");
    // the year right behind the month: a month brings its year along, and the year typed next replaces it
    assertThat(withParameters.parameters()).isEqualTo("monat:month,jahr:year");
    assertThat(withParameters.note()).isEqualTo(PaletteText.of("main.palette.context.report.parameters", "monat, jahr"));
    assertThat(suggestions.get(1).parameters()).isEmpty();
    assertThat(suggestions.get(1).note()).isNull();
  }

  @Test
  void the_command_report_offers_nothing_a_person_may_not_execute() {
    assertThat(provider.suggest(reportRequest("umsatz"))).isEmpty();

    grant(EXECUTE, "8");
    assertThat(provider.suggest(reportRequest("umsatz"))).extracting(PaletteSuggestion::value).containsExactly("8");
  }

  @Test
  void the_report_whose_name_is_typed_whole_is_exact_and_first() {
    actingAsManager();

    var suggestions = provider.suggest(reportRequest("umsatzübersicht"));

    assertThat(suggestions.getFirst().value()).isEqualTo("8");
    assertThat(suggestions.getFirst().exact()).isTrue();
  }

  @Test
  void other_commands_get_no_reports() {
    actingAsManager();

    var request = new PaletteSuggestionRequest(PaletteCommand.CONTROLLING, PaletteParameter.CUSTOMERORDER,
        PaletteQuery.of("umsatz"), null, null);

    assertThat(provider.suggest(request)).isEmpty();
  }

  @Test
  void the_year_follows_the_month_and_everything_else_keeps_the_order_of_the_sql() {
    assertThat(ReportingPaletteProvider.paletteOrder(List.of("jahr", "auftrag", "monat", "von")))
        .containsExactly("auftrag", "monat", "jahr", "von");
    assertThat(ReportingPaletteProvider.paletteOrder(List.of("jahr", "auftrag"))).containsExactly("jahr", "auftrag");
  }

  private static PaletteSuggestionRequest reportRequest(String text) {
    return new PaletteSuggestionRequest(PaletteCommand.REPORT, PaletteParameter.REPORT, PaletteQuery.of(text), null, null);
  }

}
