package org.tb.etl.service;

import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.tb.common.exception.ErrorCode.ETL_INVALID_DATE_RANGE;
import static org.tb.etl.domain.ETLRunHistory.Status.FAILED;
import static org.tb.etl.domain.ETLRunHistory.Status.RUNNING;
import static org.tb.etl.domain.ETLRunHistory.Status.SUCCEEDED;
import static org.tb.etl.domain.ETLRunHistory.Trigger.MANUAL;
import static org.tb.etl.domain.ETLRunHistory.Trigger.SCHEDULED;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.tb.auth.domain.AccessLevel;
import org.tb.common.LocalDateRange;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateTimeUtils;
import org.tb.common.util.DateUtils;
import org.tb.etl.auth.ETLAuthorization;
import org.tb.etl.domain.ETLDefinition;
import org.tb.etl.domain.ETLDefinition.ReferencePeriod;
import org.tb.etl.domain.ETLExecutionHistory;
import org.tb.etl.domain.ETLRunHistory;
import org.tb.etl.domain.ETLRunHistory.Status;
import org.tb.etl.persistence.ETLDefinitionRepository;
import org.tb.etl.persistence.ETLExecutionHistoryRepository;
import org.tb.etl.persistence.ETLRunHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class ETLService {

  /** Grenze der Spalte {@code etl_run_history.message}. */
  private static final int MESSAGE_MAX_LENGTH = 4000;

  private final ETLDefinitionRepository definitionRepo;
  private final ETLExecutionHistoryRepository historyRepo;
  private final ETLRunHistoryRepository runHistoryRepo;
  private final ParameterResolver parameterResolver;
  private final JdbcTemplate jdbc;
  private final ETLAuthorization authorization;
  private final SchemaDiffService schemaDiffService;

  @Scheduled(cron = "0 0 2 * * *") // täglich um 02:00
  public void runDaily() {
    try {
      var today = DateUtils.today();
      var range = new LocalDateRange(today.minusMonths(3), today);
      log.info("Starting scheduled daily ETL run for date range: {}", range);
      executeAll(range, true);
      log.info("Successfully completed scheduled daily ETL run");
    } catch (Exception e) {
      log.error("Scheduled daily ETL run failed", e);
      throw new RuntimeException(e);
    }
  }

  public void executeAll(LocalDateRange dateRange, boolean scheduled) {
    runWithHistory(dateRange, scheduled, this::getAllETLNames);
  }

  private List<String> getAllETLNames() {
    var definitions = definitionRepo.findAll();

    Map<String, Set<String>> etlDependencyGraph = new HashMap<>();
    for (ETLDefinition def : definitions) {
      etlDependencyGraph.put(def.getName(), def.getDependencies());
    }

    return calculateExecutionOrder(etlDependencyGraph);
  }

  @VisibleForTesting
  public List<String> calculateExecutionOrder (Map <String, Set<String>> graph) {
    List<String> executionOrder = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    Set<String> temp = new HashSet<>();

    for (String node : graph.keySet()) {
      if (!visited.contains(node)) {
        topologicalSort(node, graph, temp, visited, executionOrder);
      }
    }

    return executionOrder;
  }

  private void topologicalSort(String node, Map<String, Set<String>> graph, Set<String> temp, Set <String> visited, List <String> executionOrder) {
    if (temp.contains(node)) {
      throw new IllegalStateException("Cyclic dependency detected");
    }
    if (!visited.contains(node)) {
      temp.add(node);
      Set<String> dependencies = graph.getOrDefault(node, Set.of());
      for (String dependency : dependencies) {
        topologicalSort(dependency, graph, temp, visited, executionOrder);
      }
      temp.remove(node);
      visited.add(node);
      executionOrder.add(node);
    }
  }

  public void execute(LocalDateRange dateRange, List<String> etlNames, boolean scheduled) {
    runWithHistory(dateRange, scheduled, () -> etlNames);
  }

  /**
   * Führt einen Lauf aus und hält ihn in {@code etl_run_history} fest (#573).
   *
   * <p>Die Zeile wird <em>vor</em> der ersten Definition geschrieben und am Ende fortgeschrieben.
   * Ein Lauf, der nie zu Ende kommt, bleibt damit als {@link Status#RUNNING} ohne Endzeitpunkt
   * stehen und ist so von einem Lauf zu unterscheiden, der gar nicht erst begann.
   *
   * <p>Die Namen kommen als {@link Supplier}, weil ihre Ermittlung selbst scheitern kann: ein Zyklus
   * im Abhängigkeitsgraphen bricht den Lauf ab, bevor eine einzige Definition lief, und auch das
   * gehört in die Zeile.
   */
  private void runWithHistory(LocalDateRange dateRange, boolean scheduled, Supplier<List<String>> etlNames) {
    var run = runHistoryRepo.save(ETLRunHistory.builder()
        .startedAt(DateTimeUtils.now())
        .status(RUNNING)
        .triggeredBy(scheduled ? SCHEDULED : MANUAL)
        .dateFrom(dateRange.getFrom())
        .dateUntil(dateRange.getUntil())
        .build());

    var failed = new ArrayList<String>();
    int executed = 0;
    try {
      for (String etlName : etlNames.get()) {
        if (!executeETL(etlName, dateRange, scheduled)) {
          failed.add(etlName);
        }
        executed++;
      }
    } catch (RuntimeException e) {
      log.error("ETL run aborted after {} definition(s)", executed, e);
      finishRun(run, FAILED, "Lauf abgebrochen nach %d Definition(en): %s".formatted(executed, e));
      throw e;
    }

    if (failed.isEmpty()) {
      finishRun(run, SUCCEEDED, "%d Definition(en) ausgeführt".formatted(executed));
    } else {
      finishRun(run, FAILED, "%d Definition(en) ausgeführt, fehlgeschlagen: %s"
          .formatted(executed, String.join(", ", failed)));
    }
  }

  private void finishRun(ETLRunHistory run, Status status, String message) {
    run.setFinishedAt(DateTimeUtils.now());
    run.setStatus(status);
    run.setMessage(message.length() > MESSAGE_MAX_LENGTH ? message.substring(0, MESSAGE_MAX_LENGTH) : message);
    runHistoryRepo.save(run);
  }

  /**
   * @return {@code true}, wenn jede Referenzperiode dieser Definition durchlief
   */
  private boolean executeETL(String etlName, LocalDateRange dateRange, boolean scheduled) {
    ETLDefinition def = definitionRepo.findByName(etlName)
        .orElseThrow(() -> new IllegalArgumentException("ETL not found: " + etlName));
    if (!scheduled && !authorization.isAuthorized(def, AccessLevel.EXECUTE)) {
      throw new AuthorizationException(AA_NOT_ATHORIZED);
    }

    if (dateRange.getFrom().isAfter(dateRange.getUntil())) {
      throw new InvalidDataException(ETL_INVALID_DATE_RANGE);
    }

    var refPeriods = generateReferencePeriodRanges(dateRange, def.getReferencePeriod());
    boolean allPeriodsSucceeded = true;
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
                message.append("Init SQL: ").append(sql).append("\n");
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
            message.append("Execute SQL: ").append(sql).append("\n");
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
                message.append("Cleanup SQL: ").append(sql).append("\n");
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
      } catch (RuntimeException ex) {
        // Alles jenseits des SQLs — aufgelöste Parameter, Schema-Vergleich — bricht den ganzen Lauf
        // ab, so wie bisher. Ohne diesen Zweig stünde in der Zeile aber nur das zuletzt abgesetzte
        // SQL und kein Wort darüber, woran es lag (#573).
        log.error("ETL execution failed unexpectedly: {}", etlName, ex);
        message.append("ETL execution failed: ").append(ex).append("\n");
        throw ex;
      } finally {
        historyRepo.save(ETLExecutionHistory.builder()
            .etlId(def.getId())
            .etlName(def.getName())
            .executedAt(DateTimeUtils.now()).success(success).message(message.toString()).build());
        allPeriodsSucceeded &= success;
      }
    }
    return allPeriodsSucceeded;
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
