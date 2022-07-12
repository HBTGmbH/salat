package org.tb.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.reporting.domain.ReportDefinition;

@DataJpaTest
@Import(ReportingService.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
@EnableJpaRepositories
public class ReportingServiceTest {

  @Autowired
  private ReportingService reportingService;

  @Autowired
  private EmployeeRepository employeeRepository;

  @Test
  public void should_get_report_definitions() {
    var defs = reportingService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = new ReportDefinition();
    reportDefinition.setName("test");
    reportDefinition.setSql("select * from employee");
    reportingService.save(reportDefinition);

    defs = reportingService.getReportDefinitions();
    assertThat(defs).size().isEqualTo(1);
  }

  @Test
  public void should_execute_report_definitions_without_parameters() {
    var defs = reportingService.getReportDefinitions();
    assertThat(defs).isEmpty();

    var reportDefinition = new ReportDefinition();
    reportDefinition.setName("test");
    reportDefinition.setSql("select id, sign from employee");
    reportingService.save(reportDefinition);

    var result = reportingService.execute(reportDefinition.getId(), new HashMap<>());
    assertThat(result.getColumnHeaders()).size().isEqualTo(2);
  }

  @Test
  public void should_execute_report_definitions_with_parameters_1() {

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

    var reportDefinition = new ReportDefinition();
    reportDefinition.setName("test");
    reportDefinition.setSql("select id, sign from employee where firstname like :firstname");
    reportingService.save(reportDefinition);

    var parameters = new HashMap<String, Object>();
    parameters.put("firstname", "%Klaus%");
    var result = reportingService.execute(reportDefinition.getId(), parameters);
    assertThat(result.getColumnHeaders()).size().isEqualTo(2);
    assertThat(result.getRows()).size().isEqualTo(1);
  }

  @Test
  public void should_execute_report_definitions_with_parameters_2() {

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

    var reportDefinition = new ReportDefinition();
    reportDefinition.setName("test");
    reportDefinition.setSql("select firstname from employee where lastname = :lastname order by firstname");
    reportingService.save(reportDefinition);

    var parameters = new HashMap<String, Object>();
    parameters.put("lastname", "Richarz");
    var result = reportingService.execute(reportDefinition.getId(), parameters);
    assertThat(result.getColumnHeaders()).size().isEqualTo(1);
    assertThat(result.getRows()).size().isEqualTo(2);
    var firstnameColumnName = result.getColumnHeaders().get(0).getName();
    assertThat(result.getRows().get(0).getColumnValues().get(firstnameColumnName).getValue()).isEqualTo("Antje");
    assertThat(result.getRows().get(1).getColumnValues().get(firstnameColumnName).getValue()).isEqualTo("Klaus");
  }

}
