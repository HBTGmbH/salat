package org.tb.etl.service;

import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.tb.common.exception.ErrorCode.ETL_INVALID_DATE_RANGE;

import com.google.common.base.Stopwatch;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.tb.auth.domain.AccessLevel;
import org.tb.common.LocalDateRange;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.etl.auth.ETLAuthorization;
import org.tb.etl.domain.ETLDefinition;
import org.tb.etl.domain.ETLDefinition.ReferencePeriod;
import org.tb.etl.domain.ETLExecutionHistory;
import org.tb.etl.persistence.ETLDefinitionRepository;
import org.tb.etl.persistence.ETLExecutionHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class ETLService {

  private final ETLDefinitionRepository definitionRepo;
  private final ETLExecutionHistoryRepository historyRepo;
  private final ParameterResolver parameterResolver;
  private final JdbcTemplate jdbc;
  private final ETLAuthorization authorization;
  private final SchemaDiffService schemaDiffService;

  @Scheduled(cron = "0 0 2 * * *") // täglich um 21:00
  public void runDaily() {
    try {
      var today = DateUtils.today();
      var range = new LocalDateRange(today.minusMonths(3), today);
      log.info("Starting scheduled daily ETL run for date range: {}", range);
      executeAll(range);
      log.info("Successfully completed scheduled daily ETL run");
    } catch (Exception e) {
      log.error("Scheduled daily ETL run failed", e);
      throw new RuntimeException(e);
    }
  }

  public void executeAll(LocalDateRange dateRange, boolean scheduled) {
    execute(dateRange, getAllETLNames(), scheduled);
  }

  public void execute(LocalDateRange dateRange, Set<String> etlNames, boolean scheduled) {
    for (String etlName : etlNames) {
      executeETL(etlName, dateRange, scheduled);
    }
  }

  private Set<String> getAllETLNames() {
    return definitionRepo.findAll().stream().map(ETLDefinition::getName).collect(Collectors.toSet());
  }

  private void executeETL(String etlName, LocalDateRange dateRange, boolean scheduled) {
    ETLDefinition def = definitionRepo.findByName(etlName)
        .orElseThrow(() -> new IllegalArgumentException("ETL not found: " + etlName));
    if (!scheduled && !authorization.isAuthorized(def, AccessLevel.EXECUTE)) {
      throw new AuthorizationException(AA_NOT_ATHORIZED);
    }

    if (dateRange.getFrom().isAfter(dateRange.getUntil())) {
      throw new InvalidDataException(ETL_INVALID_DATE_RANGE);
    }

    var refPeriods = generateReferencePeriodRanges(dateRange, def.getReferencePeriod());
    for (LocalDateRange refPeriod : refPeriods) {
      boolean success = false;
      StringBuilder message = new StringBuilder();
      message.append("Date Range: ").append(refPeriod).append("\n");

      try {
        var initDiff = schemaDiffService.diffAround(
            () -> {
              var stopwatch = Stopwatch.createStarted();
              int initRows = 0;
              for (String rawSql : def.getInit().getStatements()) {
                String sql = parameterResolver.resolve(rawSql, refPeriod);
                log.debug("Send init SQL: {}", sql);
                initRows += jdbc.update(sql);
              }
              stopwatch.stop();
              message.append("Init took ").append(stopwatch).append(" (").append(initRows).append(" rows affected, ");
            },
            "salat"
        );
        message.append(initDiff.created().size()).append(" tables/objects created)\n");

        {
          var stopwatch = Stopwatch.createStarted();
          int executeRows = 0;
          for (String rawSql : def.getExecute().getStatements()) {
            String sql = parameterResolver.resolve(rawSql, refPeriod);
            log.debug("Send execute SQL: {}", sql);
            executeRows += jdbc.update(sql);
          }
          stopwatch.stop();
          message.append("Execute took ").append(stopwatch).append(" (").append(executeRows).append(" rows affected)\n");
        }

        var cleanupDiff = schemaDiffService.diffAround(
            () -> {
              var stopwatch = Stopwatch.createStarted();
              int cleanupRows = 0;
              for (String rawSql : def.getCleanup().getStatements()) {
                String sql = parameterResolver.resolve(rawSql, refPeriod);
                log.debug("Send cleanup SQL: {}", sql);
                cleanupRows += jdbc.update(sql);
              }
              stopwatch.stop();
              message.append("Cleanup took ").append(stopwatch).append(" (").append(cleanupRows).append(" rows affected, ");
            },
            "salat"
        );
        message.append(cleanupDiff.dropped().size()).append(" tables/objects dropped)\n");

        success = true;
      } catch (DataAccessException ex) {
        log.error("ETL execution failed: {}", etlName, ex);
        message.append("ETL execution failed: ").append(ex.getMessage()).append("\n");
      } finally {
        historyRepo.save(ETLExecutionHistory.builder()
            .etlId(def.getId())
            .etlName(def.getName())
            .executedAt(LocalDateTime.now()).success(success).message(message.toString()).build());
      }
    }
  }

  private List<LocalDateRange> generateReferencePeriodRanges(LocalDateRange dateRange, ReferencePeriod referencePeriod) {
    return switch (referencePeriod) {
      case YEAR -> splitRangeByPeriod(dateRange,
          date -> date.withDayOfYear(1),
          date -> date.with(TemporalAdjusters.lastDayOfYear()));
      case QUARTER -> splitRangeByPeriod(dateRange,
          date -> date.with(date.getMonth().firstMonthOfQuarter()).withDayOfMonth(1),
          date -> date.with(date.getMonth().firstMonthOfQuarter()).plusMonths(2)
              .with(TemporalAdjusters.lastDayOfMonth()));
      case MONTH -> splitRangeByPeriod(dateRange,
          date -> date.withDayOfMonth(1),
          date -> date.with(TemporalAdjusters.lastDayOfMonth()));
      case WEEK -> splitRangeByPeriod(dateRange,
          date -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
          date -> date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
      case DAY -> splitRangeByPeriod(dateRange,
          date -> date,
          date -> date);
    };
  }

  private List<LocalDateRange> splitRangeByPeriod(LocalDateRange range,
      Function<LocalDate, LocalDate> periodStart,
      Function<LocalDate, LocalDate> periodEnd) {
    List<LocalDateRange> ranges = new ArrayList<>();
    LocalDate current = range.getFrom();

    while (!current.isAfter(range.getUntil())) {
      LocalDate start = periodStart.apply(current);
      LocalDate end = periodEnd.apply(current);

      ranges.add(new LocalDateRange(start, end));
      current = end.plusDays(1);
    }

    return ranges;
  }

  public boolean isETLExisting(String etlName) {
    return definitionRepo.findByName(etlName).isPresent();
  }
}
