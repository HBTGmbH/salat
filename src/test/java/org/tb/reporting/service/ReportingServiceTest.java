package org.tb.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;

import java.util.HashMap;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.web.WebAppConfiguration;
import org.tb.auth.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.common.GlobalConstants;
import org.tb.common.SalatProperties;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.reporting.auth.ReportAuthorization;
import org.tb.testutils.WebContextTestExecutionListener;

@DataJpaTest
@Import({ReportingService.class, AuthorizedUser.class, AuthService.class, SalatProperties.class, ReportAuthorization.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
@EnableJpaRepositories
@WebAppConfiguration
@TestExecutionListeners(listeners = WebContextTestExecutionListener.class, mergeMode = MERGE_WITH_DEFAULTS)
public class ReportingServiceTest {

  @Autowired
  private ReportingService reportingService;

  @Autowired
  private EmployeeRepository employeeRepository;

  @Autowired
  private AuthorizedUser authorizedUser;

  @Test
  public void should_get_report_definitions() {
    authorizedUser.setManager(true);

    var defs = reportingService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportingService.create("test", "select id, sign from employee");
    assertThat(reportDefinition).isNotNull();

    defs = reportingService.getReportDefinitions();
    assertThat(defs).size().isEqualTo(1);
  }

  @Test
  public void should_execute_report_definitions_without_parameters() {
    authorizedUser.setManager(true);

    var defs = reportingService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportingService.create("test", "select id, sign from employee");

    var result = reportingService.execute(reportDefinition.getId(), new HashMap<>());
    assertThat(result.getColumnHeaders()).size().isEqualTo(2);
  }

  @Test
  public void should_execute_report_definitions_with_parameters_1() {
    authorizedUser.setManager(true);

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

    var defs = reportingService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportingService.create(
            "test",
            "select id, sign from employee where firstname like :firstname"
    );

    var parameters = new HashMap<String, Object>();
    parameters.put("firstname", "%Klaus%");
    var result = reportingService.execute(reportDefinition.getId(), parameters);
    assertThat(result.getColumnHeaders()).size().isEqualTo(2);
    assertThat(result.getRows()).size().isEqualTo(1);
  }

  @Test
  public void should_execute_report_definitions_with_parameters_2() {
    authorizedUser.setManager(true);

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

    var defs = reportingService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportingService.create(
            "test",
            "select firstname from employee where lastname = :lastname order by firstname"
    );

    var parameters = new HashMap<String, Object>();
    parameters.put("lastname", "Richarz");
    var result = reportingService.execute(reportDefinition.getId(), parameters);
    assertThat(result.getColumnHeaders()).size().isEqualTo(1);
    assertThat(result.getRows()).size().isEqualTo(2);
    var firstnameColumnName = result.getColumnHeaders().getFirst().getName();
    assertThat(result.getRows().getFirst().getColumnValues().get(firstnameColumnName).getValue()).isEqualTo("Antje");
    assertThat(result.getRows().get(1).getColumnValues().get(firstnameColumnName).getValue()).isEqualTo("Klaus");
  }

  @Test
  public void should_respect_alias_names_in_queries() {
    authorizedUser.setManager(true);

    var defs = reportingService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportingService.create("test", "select id, sign as sign_alias from employee");

    var result = reportingService.execute(reportDefinition.getId(), new HashMap<>());
    assertThat(result.getColumnHeaders()).size().isEqualTo(2);
    assertThat(result.getColumnHeaders()).anyMatch(header -> header.getName().equalsIgnoreCase("id"));
    assertThat(result.getColumnHeaders()).anyMatch(header -> header.getName().equalsIgnoreCase("sign_alias"));
  }

  @Test
  public void should_execute_report_with_duplicate_column_and_different_alias() {
    authorizedUser.setManager(true);

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

    var defs = reportingService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = reportingService.create(
            "test",
            "select id, sign as sign_alias_1, sign as sign_alias_2 from employee"
    );

    var result = reportingService.execute(reportDefinition.getId(), new HashMap<>());
    assertThat(result.getColumnHeaders()).size().isEqualTo(3);
    assertThat(result.getRows()).size().isEqualTo(2);
    assertThat(result.getColumnHeaders()).anyMatch(header -> header.getName().equalsIgnoreCase("id"));
    assertThat(result.getColumnHeaders()).anyMatch(header -> header.getName().equalsIgnoreCase("sign_alias_1"));
    assertThat(result.getColumnHeaders()).anyMatch(header -> header.getName().equalsIgnoreCase("sign_alias_2"));
  }

}
