package org.tb.reporting.service;

import static org.tb.auth.domain.AccessLevel.DELETE;
import static org.tb.auth.domain.AccessLevel.EXECUTE;
import static org.tb.auth.domain.AccessLevel.WRITE;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.IteratorUtils;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.reporting.auth.ReportAuthorization;
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
@DependsOnDatabaseInitialization
@Authorized
public class ReportingService {

  private final ReportDefinitionRepository reportDefinitionRepository;
  private final DataSource dataSource;
  private final ReportAuthorization reportAuthorization;
  private final AuthorizedUser authorizedUser;

  public List<ReportDefinition> getReportDefinitions() {
    return IteratorUtils.toList(
        reportDefinitionRepository.findAll(Sort.by(ReportDefinition_.NAME)).iterator()
    ).stream().filter(r -> reportAuthorization.isAuthorized(r, EXECUTE)).toList();
  }

  @Authorized(requiresManager = true)
  public void deleteReportDefinition(long reportDefinitionId) {
    reportDefinitionRepository.findById(reportDefinitionId).ifPresent(report -> {
      if(reportAuthorization.isAuthorized(report, DELETE)) {
        reportDefinitionRepository.delete(report);
      }
    });
  }

  public ReportDefinition getReportDefinition(long reportDefinitionId) {
    return reportDefinitionRepository.findById(reportDefinitionId)
        .filter(report -> reportAuthorization.isAuthorized(report, EXECUTE))
        .orElseThrow();
  }

  @Authorized(requiresManager = true)
  public ReportDefinition create(String name, String sql) {
    if(!reportAuthorization.isAuthorizedForAnyReportDefinition(WRITE)) {
      return null;
    }
    var reportDefinition = new ReportDefinition();
    reportDefinition.setName(name);
    reportDefinition.setSql(sql);
    reportDefinitionRepository.save(reportDefinition);
    return reportDefinition;
  }

  @Authorized(requiresManager = true)
  public void update(long reportDefinitionId, String name, String sql) {
    var reportDefinition = reportDefinitionRepository.findById(reportDefinitionId).orElseThrow();
    if(!reportAuthorization.isAuthorized(reportDefinition, WRITE)) {
      return;
    }
    reportDefinition.setName(name);
    reportDefinition.setSql(sql);
    reportDefinitionRepository.save(reportDefinition);
  }

  public ReportResult execute(Long reportDefinitionId, Map<String, Object> parameters) {
    var reportDefinition = reportDefinitionRepository.findById(reportDefinitionId);
    if(reportDefinition.isEmpty()) {
      throw new IllegalArgumentException("No report definition found for " + reportDefinitionId);
    }

    String sql = reportDefinition.get().getSql();
    if(sql != null && authorizedUser != null && authorizedUser.getSign() != null) {
      sql = sql.replace("###-AUTH-USER-SIGN-###", authorizedUser.getSign()); // ensure the sign is filled in as requested
    }
    final var rowset = new NamedParameterJdbcTemplate(dataSource).queryForRowSet(sql, parameters);
    var result = new ReportResult();

    // get and create headers
    var columnCount = rowset.getMetaData().getColumnCount();
    var headers = IntStream.rangeClosed(1, columnCount)
            .mapToObj(index -> rowset.getMetaData().getColumnLabel(index))
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
            if(!rowset.wasNull() && value.getClass() == java.sql.Date.class) {
              value = ((java.sql.Date) value).toLocalDate();
            }
            return new ReportResultColumnValue(value);
          }));
      row.getColumnValues().putAll(values);
      result.getRows().add(row);
      rowAvailable = rowset.next();
    }

    return result;
  }

}
