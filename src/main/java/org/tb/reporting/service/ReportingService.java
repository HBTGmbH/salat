package org.tb.reporting.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.IteratorUtils;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportDefinition_;
import org.tb.reporting.domain.ReportResult;
import org.tb.reporting.domain.ReportResultColumnHeader;
import org.tb.reporting.domain.ReportResultColumnValue;
import org.tb.reporting.domain.ReportResultRow;
import org.tb.reporting.persistence.ReportDefinitionRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportingService {

  private final ReportDefinitionRepository reportDefinitionRepository;
  private final DataSource dataSource;

  public List<ReportDefinition> getReportDefinitions() {
    return IteratorUtils.toList(
        reportDefinitionRepository.findAll(Sort.by(ReportDefinition_.NAME)).iterator()
    );
  }

  public void save(ReportDefinition reportDefinition) {
    reportDefinitionRepository.save(reportDefinition);
  }

  public ReportResult execute(Long reportDefinitionId, Map<String, Object> parameters) {
    var reportDefinition = reportDefinitionRepository.findById(reportDefinitionId);
    if(reportDefinition.isEmpty()) {
      throw new IllegalArgumentException("No report definition found for " + reportDefinitionId);
    }

    final var rowset = new NamedParameterJdbcTemplate(dataSource).queryForRowSet(reportDefinition.get().getSql(), parameters);
    var result = new ReportResult();

    // get and create headers
    var headers = Arrays.stream(rowset.getMetaData().getColumnNames())
          .map(ReportResultColumnHeader::new)
          .collect(Collectors.toList());
    result.getColumnHeaders().addAll(headers);

    // get and create rows
    var rowAvailable = rowset.first();
    while(rowAvailable) {
      final var row = new ReportResultRow();
      var values = result.getColumnHeaders().stream()
          .collect(Collectors.toMap(ReportResultColumnHeader::getName, header -> {
            var value = rowset.getObject(header.getName());
            return new ReportResultColumnValue(value);
          }));
      row.getColumnValues().putAll(values);
      result.getRows().add(row);
      rowAvailable = rowset.next();
    }

    return result;
  }

}
