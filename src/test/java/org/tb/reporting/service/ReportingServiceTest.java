package org.tb.reporting.service;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.web.WebAppConfiguration;
import org.tb.auth.AuthorizedUser;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({ReportingService.class, AuthorizedUser.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
@EnableJpaRepositories
@WebAppConfiguration
public class ReportingServiceTest {

  @Autowired
  private ReportingService reportingService;

  @Autowired
  private EmployeeRepository employeeRepository;

  @Test
  public void should_get_report_definitions() {
    var authorizedUser = new AuthorizedUser();
    authorizedUser.setManager(true);

    var defs = reportingService.getReportDefinitions(authorizedUser);
    assertThat(defs).isEmpty();

    var reportDefinition = reportingService.create(authorizedUser, "test", "select id, sign from employee");

    defs = reportingService.getReportDefinitions(authorizedUser);
    assertThat(defs).size().isEqualTo(1);
  }

  @Test
  public void should_execute_report_definitions_without_parameters() {
    var authorizedUser = new AuthorizedUser();
    authorizedUser.setManager(true);

    var defs = reportingService.getReportDefinitions(authorizedUser);
    assertThat(defs).isEmpty();

    var reportDefinition = reportingService.create(authorizedUser, "test", "select id, sign from employee");

    var result = reportingService.execute(authorizedUser, reportDefinition.getId(), new HashMap<>());
    assertThat(result.getColumnHeaders()).size().isEqualTo(2);
  }

  @Test
  public void should_execute_report_definitions_with_parameters_1() {
    var authorizedUser = new AuthorizedUser();
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

    var defs = reportingService.getReportDefinitions(authorizedUser);
    assertThat(defs).isEmpty();

    var reportDefinition = reportingService.create(
            authorizedUser,
            "test",
            "select id, sign from employee where firstname like :firstname"
    );

    var parameters = new HashMap<String, Object>();
    parameters.put("firstname", "%Klaus%");
    var result = reportingService.execute(authorizedUser, reportDefinition.getId(), parameters);
    assertThat(result.getColumnHeaders()).size().isEqualTo(2);
    assertThat(result.getRows()).size().isEqualTo(1);
  }

  @Test
  public void should_execute_report_definitions_with_parameters_2() {
    var authorizedUser = new AuthorizedUser();
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

    var defs = reportingService.getReportDefinitions(authorizedUser);
    assertThat(defs).isEmpty();

    var reportDefinition = reportingService.create(
            authorizedUser,
            "test",
            "select firstname from employee where lastname = :lastname order by firstname"
    );

    var parameters = new HashMap<String, Object>();
    parameters.put("lastname", "Richarz");
    var result = reportingService.execute(authorizedUser, reportDefinition.getId(), parameters);
    assertThat(result.getColumnHeaders()).size().isEqualTo(1);
    assertThat(result.getRows()).size().isEqualTo(2);
    var firstnameColumnName = result.getColumnHeaders().get(0).getName();
    assertThat(result.getRows().get(0).getColumnValues().get(firstnameColumnName).getValue()).isEqualTo("Antje");
    assertThat(result.getRows().get(1).getColumnValues().get(firstnameColumnName).getValue()).isEqualTo("Klaus");
  }

}
