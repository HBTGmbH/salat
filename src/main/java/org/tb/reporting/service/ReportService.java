package org.tb.reporting.service;

import static org.tb.auth.domain.AccessLevel.DELETE;
import static org.tb.auth.domain.AccessLevel.EXECUTE;
import static org.tb.auth.domain.AccessLevel.WRITE;
import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.tb.common.exception.ErrorCode.RP_REPORT_EXECUTION_FAILED;
import static org.tb.common.exception.ErrorCode.RP_REPORT_NAME_AMBIGUOUS;
import static org.tb.common.exception.ErrorCode.RP_REPORT_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.RP_REPORT_PARAMETERS_MISSING;

import java.time.LocalDate;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.IteratorUtils;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
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
        reportDefinitionRepository.findAll(Sort.by(Order.by(ReportDefinition_.NAME).ignoreCase())).iterator()
    ).stream().filter(r -> reportAuthorization.isAuthorized(r, EXECUTE)).toList();
  }

  public List<ReportDefinition> getReportDefinitionsByFilter(String filter) {
    if (filter == null || filter.isBlank()) {
      return getReportDefinitions();
    }
    return IteratorUtils.toList(
        reportDefinitionRepository.findAllByFilter(
            "%" + filter + "%",
            Sort.by(Order.by(ReportDefinition_.NAME).ignoreCase())
        ).iterator()
    ).stream().filter(r -> reportAuthorization.isAuthorized(r, EXECUTE)).toList();
  }

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
        .orElse(null);
  }

  /**
   * Liefert den Report mit genau diesem Namen. Anders als {@link #getReportDefinition(long)} sagt
   * diese Methode, warum es kein Ergebnis gibt: ein API-Aufrufer soll einen Tippfehler im Namen von
   * einer fehlenden Berechtigung unterscheiden können.
   *
   * <p>Bei mehreren Reports gleichen Namens wird keiner ausgeführt. Einen davon zu nehmen wäre ein
   * stilles falsches Ergebnis, und welcher es wäre, entschiede die Reihenfolge der Datenbank.
   */
  public ReportDefinition getReportDefinitionByName(String name) {
    var matches = reportDefinitionRepository.findAllByName(name);
    if (matches.size() > 1) {
      throw new BusinessRuleException(RP_REPORT_NAME_AMBIGUOUS, name, matches.size());
    }
    var report = matches.stream().findFirst()
        .orElseThrow(() -> new InvalidDataException(RP_REPORT_NOT_FOUND, name));
    if (!reportAuthorization.isAuthorized(report, EXECUTE)) {
      throw new AuthorizationException(AA_NOT_ATHORIZED);
    }
    return report;
  }

  /**
   * Führt einen Report aus und macht aus jedem Fehlschlag eine Ausnahme. {@link #execute(Long, List)}
   * liefert einen Fehler als Teil des Ergebnisses, weil die Ergebnisansicht ihn anzeigt und das
   * fehlgeschlagene SQL dazu — für einen Aufrufer ohne Bildschirm ist ein Ergebnis mit
   * {@code error = true} dagegen nicht von einem leeren Report zu unterscheiden.
   */
  public ReportResult executeChecked(ReportDefinition reportDefinition, List<ReportParameter> parameters) {
    var missing = ReportParameters.missing(parameters, reportDefinition.getSql());
    if (!missing.isEmpty()) {
      throw new InvalidDataException(RP_REPORT_PARAMETERS_MISSING, String.join(", ", new TreeSet<>(missing)));
    }
    var result = execute(reportDefinition.getId(), parameters);
    if (result.isError()) {
      var errorInfo = result.getErrorInfo();
      log.warn("Report {} could not be executed: {}", reportDefinition.getName(),
          errorInfo != null ? errorInfo.getErrorMessage() : null);
      throw new BusinessRuleException(RP_REPORT_EXECUTION_FAILED, reportDefinition.getName());
    }
    return result;
  }

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
    var reportDefinition = getReportDefinition(reportDefinitionId);
    if(reportDefinition == null) {
      throw new IllegalArgumentException("No report definition found for " + reportDefinitionId);
    }

    var resultBuilder = ReportResult.builder().parameters(parameters);

    String resolvedSql = reportDefinition.getSql();
    if(resolvedSql != null) {
      if (authorizedUser != null && authorizedUser.getEffectiveLoginSign() != null) {
        resolvedSql = resolvedSql.replace("###-AUTH-USER-SIGN-###", authorizedUser.getEffectiveLoginSign()); // ensure the sign is filled in as requested
      }
      // Resolve reporting placeholders based only on today's date (no FROM/UNTIL)
      LocalDate today = DateUtils.today();
      resolvedSql = reportParameterResolver.resolve(resolvedSql, today);
    }

    try {
      final var rowset = new NamedParameterJdbcTemplate(dataSource)
          .queryForRowSet(resolvedSql, ReportParameters.toParameterMap(parameters));

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

      return resultBuilder.error(false).sql(resolvedSql).build();
    } catch (BadSqlGrammarException e) {
      // capture detailed SQL error information in the result to display in UI
      var sqlEx = e.getSQLException();
      var msg = e.getMostSpecificCause().getMessage();
      log.warn("Bad SQL grammar while executing report {}: {}", reportDefinitionId, msg);
      var errorInfo = ReportResult.ErrorInfo.builder()
          .errorClass(e.getClass().getSimpleName())
          .errorMessage(msg)
          .sqlState(sqlEx != null ? sqlEx.getSQLState() : null)
          .errorCode(sqlEx != null ? sqlEx.getErrorCode() : null)
          .build();
      return ReportResult.builder()
          .parameters(parameters)
          .columnHeaders(List.of())
          .error(true)
          .errorInfo(errorInfo)
          .sql(resolvedSql) // the view offers "Show failing SQL", so the statement has to come along
          .build();
    }
  }

}
