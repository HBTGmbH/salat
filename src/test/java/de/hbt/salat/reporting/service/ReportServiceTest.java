package de.hbt.salat.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;
import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static de.hbt.salat.common.exception.ErrorCode.RP_REPORT_EXECUTION_FAILED;
import static de.hbt.salat.common.exception.ErrorCode.RP_REPORT_ID_NOT_FOUND;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.web.WebAppConfiguration;
import de.hbt.salat.auth.domain.AuthUiStateKeyContributor;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.palette.PaletteQuery;
import de.hbt.salat.common.web.UiState;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.persistence.EmployeeRepository;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.reporting.auth.ReportAuthorization;
import de.hbt.salat.reporting.domain.ReportGroup;
import de.hbt.salat.reporting.domain.ReportParameter;
import de.hbt.salat.reporting.persistence.OwnerReferences;
import de.hbt.salat.reporting.persistence.ReportGroupRepository;
import de.hbt.salat.testutils.WebContextTestExecutionListener;

@DataJpaTest
@Import({ReportService.class, AuthorizedUser.class, AuthService.class, SalatProperties.class,
    ReportAuthorization.class, UiState.class,
    AuthUiStateKeyContributor.class, OwnerReferences.class, ReportGroupNames.class, ReportServiceTest.Messages.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
@EnableJpaRepositories
@WebAppConfiguration
@TestExecutionListeners(listeners = WebContextTestExecutionListener.class, mergeMode = MERGE_WITH_DEFAULTS)
public class ReportServiceTest {

  @Autowired
  private ReportService reportService;

  @Autowired
  private EmployeeRepository employeeRepository;

  @Autowired
  private ReportGroupRepository reportGroupRepository;

  /** The names of personal groups (#1523); no group here is personal. */
  @MockitoBean
  private EmployeeService employeeService;

  /** Die Meldungen beider Sprachen, in der Sprache der Anfrage (#1570). */
  @TestConfiguration
  static class Messages {

    @Bean
    MessageSourceAccessor messageSourceAccessor() {
      var messageSource = new ResourceBundleMessageSource();
      messageSource.setBasename("de/hbt/salat/web/MessageResources");
      messageSource.setDefaultEncoding("UTF-8");
      messageSource.setFallbackToSystemLocale(false);
      return new MessageSourceAccessor(messageSource);
    }
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
    LocaleContextHolder.resetLocaleContext();
  }

  private void loginAsManager(String loginname) {
    var authorities = List.of(
        new SimpleGrantedAuthority("ROLE_USER"),
        new SimpleGrantedAuthority("ROLE_MANAGER"),
        new SimpleGrantedAuthority("ROLE_PEOPLE_LEAD"),
        new SimpleGrantedAuthority("ROLE_BACKOFFICE")
    );
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(loginname, "N/A", authorities));
  }

  private void loginAsEmployee(String loginname) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(loginname, "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }

  /** A title is unique within its group (#1523), case aside; the form asks before it saves. */
  @Test
  public void a_title_another_report_of_the_group_carries_is_taken() {
    loginAsManager("test");
    var reportDefinition = reportService.create("Umsatz", "select 1", null);

    assertThat(reportService.isNameTaken("Umsatz", null, null)).isTrue();
    assertThat(reportService.isNameTaken("umsatz", null, null)).isTrue();
    assertThat(reportService.isNameTaken("Umsatz", null, reportDefinition.getId())).isFalse();
    assertThat(reportService.isNameTaken("Auslastung", null, null)).isFalse();
  }

  /** Without a group the database cannot keep titles apart (NULL), in different groups they may repeat. */
  @Test
  public void a_title_is_free_in_another_group_and_taken_in_its_own() {
    loginAsManager("test");
    var controlling = group("Controlling");
    var personal = group("Personal");
    reportService.create("Umsatz", "select 1", controlling.getId());

    assertThat(reportService.isNameTaken("Umsatz", controlling.getId(), null)).isTrue();
    assertThat(reportService.isNameTaken("Umsatz", personal.getId(), null)).isFalse();
    assertThat(reportService.isNameTaken("Umsatz", null, null)).isFalse();
  }

  private ReportGroup group(String name) {
    var group = new ReportGroup();
    group.setName(name);
    return reportGroupRepository.save(group);
  }

  @Test
  public void should_get_report_definition_by_id() {
    loginAsManager("test");
    var reportDefinition = reportService.create("test", "select 1", null);

    assertThat(reportService.getReportDefinitionById(reportDefinition.getId()))
        .isEqualTo(reportDefinition);
  }

  @Test
  public void should_tell_an_unknown_id_apart_from_a_missing_permission() {
    loginAsManager("test");
    var reportDefinition = reportService.create("test", "select 1", null);
    var unknownId = reportDefinition.getId() + 1000;

    assertThatThrownBy(() -> reportService.getReportDefinitionById(unknownId))
        .isInstanceOfSatisfying(InvalidDataException.class, e -> {
          assertThat(e.getMessages().getFirst().getErrorCode()).isEqualTo(RP_REPORT_ID_NOT_FOUND);
          assertThat(e.getMessages().getFirst().getArguments()).containsExactly(String.valueOf(unknownId));
        });

    loginAsEmployee("other");
    assertThatThrownBy(() -> reportService.getReportDefinitionById(reportDefinition.getId()))
        .isInstanceOfSatisfying(AuthorizationException.class,
            e -> assertThat(e.getMessages().getFirst().getErrorCode()).isEqualTo(AA_NOT_ATHORIZED));
  }

  @Test
  public void should_get_report_definitions() {
    loginAsManager("test");

    var defs = reportService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportService.create("test", "select id, sign from employee", null);
    assertThat(reportDefinition).isNotNull();

    defs = reportService.getReportDefinitions();
    assertThat(defs).size().isEqualTo(1);
  }

  @Test
  public void should_execute_report_definitions_without_parameters() {
    loginAsManager("test");

    var defs = reportService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportService.create("test", "select id, sign from employee", null);

    var result = reportService.execute(reportDefinition.getId(), List.of());
    assertThat(result.getColumnHeaders()).size().isEqualTo(2);
  }

  @Test
  public void should_execute_report_definitions_with_parameters_1() {
    loginAsManager("test");

    Employee employee = new Employee();
    employee.setSign("kr");
    employee.setFirstname("Klaus");
    employee.setLastname("Richarz");
    employee.setGender(GlobalConstants.GENDER_MALE);
    employeeRepository.save(employee);
    employee = new Employee();
    employee.setSign("ar");
    employee.setFirstname("Antje");
    employee.setLastname("Richarz");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employeeRepository.save(employee);

    var defs = reportService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportService.create(
            "test",
            "select id, sign from employee where firstname like :firstname", null
    );

    var parameters = List.of(new ReportParameter("firstname", "string", "%Klaus%"));
    var result = reportService.execute(reportDefinition.getId(), parameters);
    assertThat(result.getColumnHeaders()).size().isEqualTo(2);
    assertThat(result.getRows()).size().isEqualTo(1);
  }

  @Test
  public void should_execute_report_definitions_with_parameters_2() {
    loginAsManager("test");

    Employee employee = new Employee();
    employee.setSign("kr");
    employee.setFirstname("Klaus");
    employee.setLastname("Richarz");
    employee.setGender(GlobalConstants.GENDER_MALE);
    employeeRepository.save(employee);
    employee = new Employee();
    employee.setSign("ar");
    employee.setFirstname("Antje");
    employee.setLastname("Richarz");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employeeRepository.save(employee);

    var defs = reportService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportService.create(
            "test",
            "select firstname from employee where lastname = :lastname order by firstname", null);

    var parameters = List.of(new ReportParameter("lastname", "string", "Richarz"));
    var result = reportService.execute(reportDefinition.getId(), parameters);
    assertThat(result.getColumnHeaders()).size().isEqualTo(1);
    assertThat(result.getRows()).size().isEqualTo(2);
    var firstnameColumnName = result.getColumnHeaders().getFirst().getName();
    assertThat(result.getRows().getFirst().getColumnValues().get(firstnameColumnName).getValue()).isEqualTo("Antje");
    assertThat(result.getRows().get(1).getColumnValues().get(firstnameColumnName).getValue()).isEqualTo("Klaus");
  }

  @Test
  public void should_respect_alias_names_in_queries() {
    loginAsManager("test");

    var defs = reportService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportService.create("test", "select id, sign as sign_alias from employee", null);

    var result = reportService.execute(reportDefinition.getId(), List.of());
    assertThat(result.getColumnHeaders()).size().isEqualTo(2);
    assertThat(result.getColumnHeaders()).anyMatch(header -> header.getName().equalsIgnoreCase("id"));
    assertThat(result.getColumnHeaders()).anyMatch(header -> header.getName().equalsIgnoreCase("sign_alias"));
  }

  /**
   * Ein Report, dessen SQL erst beim Ausführen scheitert, ist genauso ein Ergebnis der
   * Reportpflege wie ein Syntaxfehler — und keine Ausnahme, die bis zur allgemeinen Fehlerseite
   * durchläuft (#1110).
   */
  @Test
  public void should_report_an_error_that_occurs_only_while_executing() {
    loginAsManager("test");

    // syntaktisch gültig: der Teiler steht erst mit der gelesenen Zeile fest
    var sql = "select 100 / (t.n - t.n) as quotient from (select 1 as n) t";
    var reportDefinition = reportService.create("test", sql, null);

    var result = reportService.execute(reportDefinition.getId(), List.of());

    assertThat(result.isError()).isTrue();
    assertThat(result.getErrorInfo()).isNotNull();
    assertThat(result.getErrorInfo().getErrorClass()).isNotBlank();
    // der Fall, der vorher ungefangen durchlief — sonst prüft dieser Test den alten Zweig mit
    assertThat(result.getErrorInfo().getErrorClass()).isNotEqualTo("BadSqlGrammarException");
    assertThat(result.getErrorInfo().getErrorMessage()).isNotBlank();
    // die Ansicht bietet „Show failing SQL" an, also muss die Anweisung mitkommen
    assertThat(result.getSql()).isEqualTo(sql);
  }

  @Test
  public void should_report_a_syntax_error_with_sql_state_and_error_code() {
    loginAsManager("test");

    var sql = "select * from a_table_that_does_not_exist";
    var reportDefinition = reportService.create("test", sql, null);

    var result = reportService.execute(reportDefinition.getId(), List.of());

    assertThat(result.isError()).isTrue();
    assertThat(result.getErrorInfo()).isNotNull();
    assertThat(result.getErrorInfo().getErrorClass()).isEqualTo("BadSqlGrammarException");
    assertThat(result.getErrorInfo().getErrorMessage()).containsIgnoringCase("a_table_that_does_not_exist");
    assertThat(result.getErrorInfo().getSqlState()).isNotBlank();
    assertThat(result.getErrorInfo().getErrorCode()).isNotNull();
    assertThat(result.getSql()).isEqualTo(sql);
  }

  @Test
  public void should_execute_report_with_duplicate_column_and_different_alias() {
    loginAsManager("test");

    Employee employee = new Employee();
    employee.setSign("kr");
    employee.setFirstname("Klaus");
    employee.setLastname("Richarz");
    employee.setGender(GlobalConstants.GENDER_MALE);
    employeeRepository.save(employee);
    employee = new Employee();
    employee.setSign("ar");
    employee.setFirstname("Antje");
    employee.setLastname("Richarz");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employeeRepository.save(employee);

    var defs = reportService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportService.create(
            "test",
            "select id, sign as sign_alias_1, sign as sign_alias_2 from employee", null);

    var result = reportService.execute(reportDefinition.getId(), List.of());
    assertThat(result.getColumnHeaders()).size().isEqualTo(3);
    assertThat(result.getRows()).size().isEqualTo(2);
    assertThat(result.getColumnHeaders()).anyMatch(header -> header.getName().equalsIgnoreCase("id"));
    assertThat(result.getColumnHeaders()).anyMatch(header -> header.getName().equalsIgnoreCase("sign_alias_1"));
    assertThat(result.getColumnHeaders()).anyMatch(header -> header.getName().equalsIgnoreCase("sign_alias_2"));
  }

  /**
   * Zwei Spalten mit demselben Namen sind ein Fehler des Reports wie ein SQL-Fehler: das Ergebnis
   * trägt ihn samt SQL, statt an der Map der Zeile mit einer Ausnahme zu scheitern (#1570).
   */
  @Test
  public void a_duplicate_column_name_is_an_error_of_the_report() {
    loginAsManager("test");
    LocaleContextHolder.setLocale(Locale.GERMAN);
    // in Anführungszeichen, sonst schriebe H2 die Namen groß
    var sql = "select 1 as \"id\", 2 as \"id\"";
    var reportDefinition = reportService.create("test", sql, null);

    var result = reportService.execute(reportDefinition.getId(), List.of());

    assertThat(result.isError()).isTrue();
    assertThat(result.getErrorInfo().getErrorClass()).isEqualTo("DuplicateColumnName");
    assertThat(result.getErrorInfo().getErrorMessage())
        .isEqualTo("Mehrfach vorkommende Spaltennamen: id. Jede Spalte braucht einen eindeutigen Namen, "
            + "z. B. per Alias: e.id AS mitarbeiter_id");
    assertThat(result.getSql()).isEqualTo(sql);
    assertThat(result.getRows()).isEmpty();
  }

  @Test
  public void the_message_names_every_duplicate_column_in_english_too() {
    loginAsManager("test");
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    var reportDefinition = reportService.create("test", "select 1 as \"a\", 2 as \"b\", 3 as \"a\", 4 as \"c\", 5 as \"b\"", null);

    var result = reportService.execute(reportDefinition.getId(), List.of());

    assertThat(result.isError()).isTrue();
    assertThat(result.getErrorInfo().getErrorMessage())
        .isEqualTo("Column names occurring more than once: a, b. Every column needs a unique name, "
            + "e.g. with an alias: e.id AS employee_id");
  }

  /**
   * MySQL unterscheidet Spaltennamen nicht nach Groß- und Kleinschreibung und liest {@code Id} und
   * {@code id} als dieselbe Spalte; die Map der Zeile hielte beide und zeigte zweimal einen Wert.
   * Sie gelten deshalb als doppelt, genannt in der Schreibweise ihres ersten Auftretens.
   */
  @Test
  public void names_differing_only_in_case_count_as_duplicates() {
    loginAsManager("test");
    LocaleContextHolder.setLocale(Locale.GERMAN);
    var reportDefinition = reportService.create("test", "select 1 as \"Id\", 2 as \"id\"", null);

    var result = reportService.execute(reportDefinition.getId(), List.of());

    assertThat(result.isError()).isTrue();
    assertThat(result.getErrorInfo().getErrorMessage()).startsWith("Mehrfach vorkommende Spaltennamen: Id.");
  }

  /** Die REST-API antwortet wie bei jedem fehlgeschlagenen Report (#1570). */
  @Test
  public void a_duplicate_column_name_fails_the_checked_execution() {
    loginAsManager("test");
    var reportDefinition = reportService.create("test", "select 1 as id, 2 as id", null);

    assertThatThrownBy(() -> reportService.executeChecked(reportDefinition, List.of()))
        .isInstanceOfSatisfying(BusinessRuleException.class, e ->
            assertThat(e.getMessages()).anySatisfy(m -> assertThat(m.getErrorCode()).isEqualTo(RP_REPORT_EXECUTION_FAILED)));
  }

  /** The command palette finds a report by every word of its name, never by its SQL (#1232). */
  @Test
  public void the_palette_finds_a_report_by_the_words_of_its_name_not_by_its_sql() {
    loginAsManager("test");
    reportService.create("Umsatz je Auftrag", "select 1", null);
    reportService.create("Umsatz Planung", "select 2", null);
    reportService.create("Offene Freigaben", "select 'umsatz auftrag'", null);

    assertThat(reportService.getPaletteCandidates(PaletteQuery.of("umsatz")))
        .extracting(candidate -> candidate.report().getName()).containsExactly("Umsatz je Auftrag", "Umsatz Planung");
    assertThat(reportService.getPaletteCandidates(PaletteQuery.of("auf ums")))
        .extracting(candidate -> candidate.report().getName()).containsExactly("Umsatz je Auftrag");
  }

  /** The name of the group stands where the prefix of the title used to (#1523). */
  @Test
  public void the_palette_finds_a_report_by_the_name_of_its_group() {
    loginAsManager("test");
    reportService.create("Offene Freigaben", "select 1", group("Monatsabschluss").getId());
    reportService.create("Umsatz", "select 2", null);

    assertThat(reportService.getPaletteCandidates(PaletteQuery.of("monatsab freig")))
        .extracting(candidate -> candidate.report().getName(), candidate -> candidate.group().name())
        .containsExactly(tuple("Offene Freigaben", "Monatsabschluss"));
  }

  @Test
  public void the_palette_reads_a_percent_sign_in_the_query_as_itself() {
    loginAsManager("test");
    reportService.create("100% Auslastung", "select 1", null);
    reportService.create("1000 Stunden", "select 2", null);

    assertThat(reportService.getPaletteCandidates(PaletteQuery.of("100%")))
        .extracting(candidate -> candidate.report().getName()).containsExactly("100% Auslastung");
  }

  @Test
  public void the_palette_offers_no_report_the_person_may_not_execute() {
    loginAsManager("test");
    reportService.create("Umsatz je Auftrag", "select 1", null);

    loginAsEmployee("other");
    assertThat(reportService.getPaletteCandidates(PaletteQuery.of("umsatz"))).isEmpty();
  }

}
