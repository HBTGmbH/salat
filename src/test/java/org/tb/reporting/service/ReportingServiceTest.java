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
import org.tb.reporting.domain.ReportDefinition;

@DataJpaTest
@Import(ReportingService.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class ReportingServiceTest {

  @Autowired
  private ReportingService reportingService;

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

}
