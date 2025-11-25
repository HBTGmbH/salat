package org.tb.reporting.service;

import static org.tb.auth.domain.AccessLevel.DELETE;
import static org.tb.auth.domain.AccessLevel.EXECUTE;
import static org.tb.auth.domain.AccessLevel.WRITE;
import static org.tb.common.util.DateUtils.today;

import java.time.LocalDate;
import java.util.HashMap;
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
import org.tb.common.util.DateUtils;
import org.tb.reporting.auth.ReportAuthorization;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportDefinition_;
import org.tb.reporting.domain.ReportParameter;
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
public class ReportService {

  private final ReportDefinitionRepository reportDefinitionRepository;
  private final DataSource dataSource;
  private final ReportAuthorization reportAuthorization;
  private final AuthorizedUser authorizedUser;
  private final ReportParameterResolver reportParameterResolver;

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

  public ReportResult execute(Long reportDefinitionId, List<ReportParameter> parameters) {
    var reportDefinition = reportDefinitionRepository.findById(reportDefinitionId);
    if(reportDefinition.isEmpty()) {
      throw new IllegalArgumentException("No report definition found for " + reportDefinitionId);
    }

    var resultBuilder = ReportResult.builder().parameters(parameters);

    String sql = reportDefinition.get().getSql();
    if(sql != null) {
      if (authorizedUser != null && authorizedUser.getEffectiveLoginSign() != null) {
        sql = sql.replace("###-AUTH-USER-SIGN-###", authorizedUser.getEffectiveLoginSign()); // ensure the sign is filled in as requested
      }
      // Resolve reporting placeholders based only on today's date (no FROM/UNTIL)
      LocalDate today = LocalDate.now();
      sql = reportParameterResolver.resolve(sql, today);
    }
    final var rowset = new NamedParameterJdbcTemplate(dataSource).queryForRowSet(sql, getParameterMap(parameters));

    // get and create headers
    var columnCount = rowset.getMetaData().getColumnCount();
    var headers = IntStream.rangeClosed(1, columnCount)
            .mapToObj(index -> rowset.getMetaData().getColumnLabel(index))
            .map(ReportResultColumnHeader::new)
            .collect(Collectors.toList());
    resultBuilder.columnHeaders(headers);

    // get and create rows
    var rowAvailable = rowset.first();
    while(rowAvailable) {
      final var row = new ReportResultRow();
      var values = headers.stream()
          .collect(Collectors.toMap(ReportResultColumnHeader::getName, header -> {
            var value = rowset.getObject(header.getName());
            if(!rowset.wasNull() && value.getClass() == java.sql.Date.class) {
              value = ((java.sql.Date) value).toLocalDate();
            }
            return new ReportResultColumnValue(value);
          }));
      row.getColumnValues().putAll(values);
      resultBuilder.row(row);
      rowAvailable = rowset.next();
    }

    return resultBuilder.build();
  }

  private static Map<String, Object> getParameterMap(List<ReportParameter> parameters) {
    var result = new HashMap<String, Object>();
    for (ReportParameter parameter : nonEmpty(parameters)) {
      var name = parameter.getName();
      var value = parameter.getValue();
      if(value == null || value.isBlank()) value = "";
      value = value.replace('*','%'); // make it SQL compatible
      switch (parameter.getType()) {
        case "date" -> {
          if(value.equals("TODAY") || value.equals("HEUTE")) {
            result.put(name, today());
          } else {
            if(value.isBlank()) {
              result.put(name, null);
            } else {
              result.put(name, DateUtils.parse(value));
            }
          }
        }
        default -> result.put(name, value);
      }
    }
    return result;
  }

  private static List<ReportParameter> nonEmpty(List<ReportParameter> parameters) {
    return parameters.stream().filter(p -> p.getName() != null && !p.getName().isBlank()).toList();
  }

}
