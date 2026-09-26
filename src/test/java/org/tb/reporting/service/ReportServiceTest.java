package org.tb.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BL;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.web.WebAppConfiguration;
import org.tb.auth.domain.AuthUiStateKeyContributor;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.common.GlobalConstants;
import org.tb.common.SalatProperties;
import org.tb.common.web.UiState;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.reporting.auth.ReportAuthorization;
import org.tb.reporting.domain.ReportParameter;
import org.tb.testutils.WebContextTestExecutionListener;

@DataJpaTest
@Import({ReportService.class, AuthorizedUser.class, AuthService.class, SalatProperties.class,
    ReportAuthorization.class, UiState.class,
    AuthUiStateKeyContributor.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
@EnableJpaRepositories
@WebAppConfiguration
@TestExecutionListeners(listeners = WebContextTestExecutionListener.class, mergeMode = MERGE_WITH_DEFAULTS)
public class ReportServiceTest {

  @Autowired
  private ReportService reportService;

  @Autowired
  private EmployeeRepository employeeRepository;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
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

  @Test
  public void should_get_report_definitions() {
    loginAsManager("test");

    var defs = reportService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportService.create("test", "select id, sign from employee");
    assertThat(reportDefinition).isNotNull();

    defs = reportService.getReportDefinitions();
    assertThat(defs).size().isEqualTo(1);
  }

  @Test
  public void should_execute_report_definitions_without_parameters() {
    loginAsManager("test");

    var defs = reportService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportService.create("test", "select id, sign from employee");

    var result = reportService.execute(reportDefinition.getId(), List.of());
    assertThat(result.getColumnHeaders()).size().isEqualTo(2);
  }

  @Test
  public void should_execute_report_definitions_with_parameters_1() {
    loginAsManager("test");

    Employee employee = new Employee();
    employee.setFirstname("Klaus");
    employee.setLastname("Richarz");
    employee.setGender(GlobalConstants.GENDER_MALE);
    employeeRepository.save(employee);
    employee = new Employee();
    employee.setFirstname("Antje");
    employee.setLastname("Richarz");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employeeRepository.save(employee);

    var defs = reportService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportService.create(
            "test",
            "select id, sign from employee where firstname like :firstname"
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
    employee.setFirstname("Klaus");
    employee.setLastname("Richarz");
    employee.setGender(GlobalConstants.GENDER_MALE);
    employeeRepository.save(employee);
    employee = new Employee();
    employee.setFirstname("Antje");
    employee.setLastname("Richarz");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employeeRepository.save(employee);

    var defs = reportService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportService.create(
            "test",
            "select firstname from employee where lastname = :lastname order by firstname"
    );

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

    var reportDefinition = reportService.create("test", "select id, sign as sign_alias from employee");

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
    var reportDefinition = reportService.create("test", sql);

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
    var reportDefinition = reportService.create("test", sql);

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
            "select id, sign as sign_alias_1, sign as sign_alias_2 from employee"
    );

    var result = reportService.execute(reportDefinition.getId(), List.of());
    assertThat(result.getColumnHeaders()).size().isEqualTo(3);
    assertThat(result.getRows()).size().isEqualTo(2);
    assertThat(result.getColumnHeaders()).anyMatch(header -> header.getName().equalsIgnoreCase("id"));
    assertThat(result.getColumnHeaders()).anyMatch(header -> header.getName().equalsIgnoreCase("sign_alias_1"));
    assertThat(result.getColumnHeaders()).anyMatch(header -> header.getName().equalsIgnoreCase("sign_alias_2"));
  }

}
