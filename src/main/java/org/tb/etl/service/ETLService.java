package org.tb.etl.service;

import static java.util.Comparator.comparing;
import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.tb.common.exception.ErrorCode.ETL_CYCLIC_DEPENDENCY;
import static org.tb.common.exception.ErrorCode.ETL_DEFINITION_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.ETL_INVALID_DATE_RANGE;
import static org.tb.common.exception.ErrorCode.ETL_NO_EXECUTABLE_DEFINITION;
import static org.tb.common.exception.ErrorCode.ETL_RUN_ALREADY_RUNNING;
import static org.tb.common.exception.ErrorCode.ETL_RUN_NOT_FOUND;
import static org.tb.etl.domain.ETLRunHistory.MESSAGE_MAX_LENGTH;
import static org.tb.etl.domain.ETLRunHistory.Status.FAILED;
import static org.tb.etl.domain.ETLRunHistory.Status.RUNNING;
import static org.tb.etl.domain.ETLRunHistory.Status.SKIPPED;
import static org.tb.etl.domain.ETLRunHistory.Status.SUCCEEDED;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.Authorized;
import org.tb.common.LocalDateRange;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateTimeUtils;
import org.tb.etl.auth.ETLAuthorization;
import org.tb.etl.domain.ETLDefinition;
import org.tb.etl.domain.ETLDefinition.ReferencePeriod;
import org.tb.etl.domain.ETLDefinitionOption;
import org.tb.etl.domain.ETLExecutionHistory;
import org.tb.etl.domain.ETLRunHistory;
import org.tb.etl.domain.ETLRunHistory.Status;
import org.tb.etl.domain.ETLRunHistory.Trigger;
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

/**
 * Führt die ETL-Definitionen aus und hält jeden Lauf in {@code etl_run_history} fest.
 *
 * <p><b>Bewusst ohne {@code @Transactional}</b>, abweichend vom Muster in AGENTS.md: {@code init}
 * und {@code cleanup} einer Definition setzen DDL ab, das MySQL implizit committet, und ein Lauf
 * über drei Monate läuft in die Minuten. Eine umspannende Transaktion wäre weder haltbar noch
 * wirksam. Jede Zeile committet für sich — genau deshalb ist die {@code RUNNING}-Zeile sichtbar,
 * bevor der Lauf zu Ende ist (#573), und genau darauf stützt sich die Sperre (#1071).
 *
 * <p><b>Ein Lauf zur Zeit</b> (→ ADR-0028): die {@code RUNNING}-Zeile <em>ist</em> die Sperre.
 * {@link #startRun} prüft und schreibt sie in einem Abschnitt; jeder Weg in einen Lauf — Oberfläche,
 * REST-Schnittstelle, nächtlicher Lauf — geht durch diese Tür.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Authorized
public class ETLService {

  /**
   * Der Startzeitpunkt des blockierenden Laufs für die Absage — vorformatiert, weil
   * {@code MessageFormat} ein {@code LocalDateTime} sonst als „2026-09-24T14:03:11.123" ausgäbe.
   */
  private static final DateTimeFormatter STARTED_AT_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

  private final ETLDefinitionRepository definitionRepo;
  private final ETLExecutionHistoryRepository historyRepo;
  private final ETLRunHistoryRepository runHistoryRepo;
  private final ParameterResolver parameterResolver;
  private final JdbcTemplate jdbc;
  private final ETLAuthorization authorization;
  private final SchemaDiffService schemaDiffService;

  /**
   * Eröffnet einen Lauf: prüft den Zeitraum, weist einen zweiten gleichzeitigen Lauf ab und legt die
   * {@code RUNNING}-Zeile an (#1071).
   *
   * <p>Beides gehört zusammen und passiert im Thread des Aufrufers, nicht im Hintergrund: nur so
   * sieht ein zweiter Anstoß die Zeile, und nur so steht der Lauf schon in der Liste, auf die
   * umgeleitet wird. {@code synchronized}, weil Prüfen und Anlegen einen Abschnitt bilden — zwei
   * gleichzeitige Anfragen kämen sonst beide an der Prüfung vorbei.
   *
   * <p><b>Voraussetzung: eine Instanz der Anwendung.</b> Die Sperre wirkt innerhalb einer JVM. Bei
   * mehreren Instanzen bräuchte sie eine Absicherung in der Datenbank — in MySQL 8 etwa eine
   * generierte Spalte mit eindeutigem Index, an der der zweite Einfügeversuch scheitert. Dieselbe
   * Voraussetzung tragen die {@code @Scheduled}-Jobs der Anwendung ohnehin schon.
   *
   * @throws InvalidDataException wenn der Zeitraum verkehrt herum liegt — geprüft, <em>bevor</em>
   *     eine Zeile entsteht
   * @throws BusinessRuleException wenn bereits ein Lauf läuft
   */
  public synchronized ETLRunHistory startRun(LocalDateRange dateRange, Trigger trigger) {
    if (!dateRange.isValid()) {
      throw new InvalidDataException(ETL_INVALID_DATE_RANGE);
    }
    runHistoryRepo.findFirstByStatusOrderByStartedAtDesc(RUNNING).ifPresent(running -> {
      throw new BusinessRuleException(ETL_RUN_ALREADY_RUNNING,
          running.getStartedAt().format(STARTED_AT_FORMAT));
    });
    return runHistoryRepo.save(ETLRunHistory.builder()
        .startedAt(DateTimeUtils.now())
        .status(RUNNING)
        .triggeredBy(trigger)
        .dateFrom(dateRange.getFrom())
        .dateUntil(dateRange.getUntil())
        .build());
  }

  /**
   * Hält fest, dass ein Lauf gar nicht erst begann, weil schon einer lief (#1071).
   *
   * <p>Für den nächtlichen Lauf ist das der Unterschied zwischen „fiel aus" und „niemand weiß es":
   * ohne diese Zeile stünde der Ausfall nur im Log, und die Liste zeigte eine Lücke, die von einer
   * abgeschalteten Anwendung nicht zu unterscheiden wäre.
   */
  public void recordSkippedRun(LocalDateRange dateRange, Trigger trigger, String message) {
    var now = DateTimeUtils.now();
    runHistoryRepo.save(ETLRunHistory.builder()
        .startedAt(now)
        .finishedAt(now)
        .status(SKIPPED)
        .triggeredBy(trigger)
        .dateFrom(dateRange.getFrom())
        .dateUntil(dateRange.getUntil())
        .message(shortened(message))
        .build());
  }

  public void executeAll(LocalDateRange dateRange, Trigger trigger) {
    checkAuthorizedForAnyETL();
    runWithHistory(startRun(dateRange, trigger), dateRange, () -> {
      var names = getAllETLNames();
      names.forEach(this::checkExecutable);
      return names;
    });
  }

  private List<String> getAllETLNames() {
    return calculateExecutionOrder(dependencyGraph());
  }

  private Map<String, Set<String>> dependencyGraph() {
    Map<String, Set<String>> etlDependencyGraph = new HashMap<>();
    for (ETLDefinition def : definitionRepo.findAll()) {
      etlDependencyGraph.put(def.getName(), def.getDependencies());
    }
    return etlDependencyGraph;
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

  public void execute(LocalDateRange dateRange, List<String> etlNames, Trigger trigger) {
    checkAuthorizedForAnyETL();
    runWithHistory(startRun(dateRange, trigger), dateRange, () -> {
      etlNames.forEach(this::checkExecutable);
      return etlNames;
    });
  }

  /**
   * Die Vorprüfung, die <em>vor</em> {@link #startRun} fällt — und deshalb, bevor eine Zeile entsteht.
   *
   * <p>Die Zeile ist die Sperre. Entstünde sie vor der Rechteprüfung, könnte jede beliebige Anmeldung
   * über die REST-Schnittstelle — die nur nach Authentifizierung fragt — reihenweise Sperrzeilen
   * erzeugen und damit berechtigte Läufe und den nächtlichen Lauf abweisen lassen. Die Prüfung je
   * Definition bleibt daneben im Lauf selbst: ein Recht, das für eine einzelne Definition fehlt,
   * gehört in die Zeile des Laufs, nicht vor ihn.
   */
  private void checkAuthorizedForAnyETL() {
    if (!authorization.isAuthorizedForAnyETL(AccessLevel.EXECUTE)) {
      throw new AuthorizationException(AA_NOT_ATHORIZED);
    }
  }

  /**
   * Die Definitionen, die die anfragende Person ausführen darf (#1071) — die Auswahlliste des
   * Formulars und zugleich die Bedeutung von „alle".
   *
   * <p>Gefragt wird je Definition: {@code AuthService} beantwortet nur ja oder nein zu einem Objekt,
   * eine Abfrage „alle Objekte, für die ich eine Regel habe" gibt es nicht. Bei einer zweistelligen
   * Zahl von Definitionen ist das die richtige Antwort — sie läuft gegen den Zwischenspeicher der
   * Regeln, nicht gegen die Datenbank.
   */
  @Transactional(readOnly = true)
  public List<ETLDefinitionOption> getExecutableDefinitions() {
    return definitionRepo.findAll().stream()
        .filter(def -> authorization.isAuthorized(def, AccessLevel.EXECUTE))
        .sorted(comparing(ETLDefinition::getName))
        .map(ETLDefinitionOption::from)
        .toList();
  }

  /**
   * Klärt einen von Hand angestoßenen Lauf ab, <em>bevor</em> er entsteht (#1071): Zeitraum,
   * Berechtigung und die Reihenfolge, in der gelaufen wird.
   *
   * <p>Das läuft im Thread der Anfrage und nicht im Hintergrund, denn nur hier gibt es die
   * anfragende Person. Im Hintergrundthread arbeitet der Lauf als {@code SYSTEM}
   * ({@code AuthorizedUser.initForJob}) und käme durch jede Prüfung — eine Prüfung dort sähe aus wie
   * eine und wäre keine.
   *
   * @param etlName die gewählte Definition, oder {@code null} für „alle, die ich ausführen darf"
   * @return die Namen in der Reihenfolge, in der sie laufen — bei einer einzelnen Definition samt
   *     ihrer Abhängigkeiten
   */
  public List<String> resolveManualRun(LocalDateRange dateRange, String etlName) {
    // Die Berechtigung zuerst, die Eingabe danach: wer gar nicht anstoßen darf, soll das erfahren
    // und nicht erst eine Rückmeldung zu seiner Eingabe bekommen. Nebenbei ist das die Reihenfolge,
    // die ein Test überhaupt auseinanderhalten kann — läge die Eingabeprüfung vorn, verdeckte eine
    // ungültige Eingabe jede Aussage über das Recht.
    checkAuthorizedForAnyETL();
    if (!dateRange.isValid()) {
      throw new InvalidDataException(ETL_INVALID_DATE_RANGE);
    }

    List<String> entryPoints;
    if (etlName != null && !etlName.isBlank()) {
      checkExecutable(etlName);
      entryPoints = List.of(etlName);
    } else {
      entryPoints = getExecutableDefinitions().stream().map(ETLDefinitionOption::name).toList();
      if (entryPoints.isEmpty()) {
        throw new BusinessRuleException(ETL_NO_EXECUTABLE_DEFINITION);
      }
    }

    // Geprüft wird der Einstieg, nicht die Hülle: die Abhängigkeiten, die `executionOrderFor` hier
    // mitzieht, laufen ohne eigene Prüfung. Eine Regel für X ohne das, was X braucht, wäre sonst
    // wertlos — der Lauf bräche an der ersten Abhängigkeit ab (#1071).
    try {
      return executionOrderFor(entryPoints);
    } catch (IllegalStateException e) {
      // Auf allen anderen Wegen wird die Reihenfolge erst im Lauf ermittelt, und ein Zyklus landet
      // deshalb als Meldung in dessen Zeile. Hier wird sie vorher gebraucht — es gibt noch keine
      // Zeile, in die er könnte. Eine durchgereichte IllegalStateException wäre an dieser Stelle
      // eine Fehlerseite; die anfragende Person soll stattdessen lesen, was los ist.
      throw new BusinessRuleException(ETL_CYCLIC_DEPENDENCY, e);
    }
  }

  /**
   * Führt einen bereits eröffneten Lauf aus — der Teil, der im Hintergrund läuft (#1071).
   *
   * <p>Weitergereicht wird nur die Id, nicht die Entität — den eigentlichen Schutz davor, eine
   * zwischenzeitlich von Hand beendete Zeile zu überschreiben, leistet allerdings erst das erneute
   * Lesen in {@link #finishRun}: zwischen dem Laden hier und dem Schreiben dort liegen die Minuten
   * des Laufs.
   *
   * <p>Ohne Rechteprüfung, und das mit Absicht: sie ist in {@link #resolveManualRun} im Thread der
   * Anfrage gefallen, wo es die anfragende Person noch gibt.
   */
  public void continueRun(long runId, LocalDateRange dateRange, List<String> etlNames) {
    var run = runHistoryRepo.findById(runId)
        .orElseThrow(() -> new InvalidDataException(ETL_RUN_NOT_FOUND));
    runWithHistory(run, dateRange, () -> etlNames);
  }

  /**
   * Führt den eröffneten Lauf aus und schreibt seine Zeile fort (#573).
   *
   * <p>Die Zeile entsteht in {@link #startRun}, also <em>vor</em> der ersten Definition. Ein Lauf,
   * der nie zu Ende kommt, bleibt damit als {@link Status#RUNNING} ohne Endzeitpunkt stehen und ist
   * so von einem Lauf zu unterscheiden, der gar nicht erst begann.
   *
   * <p>Die Namen kommen als {@link Supplier}, weil ihre Ermittlung selbst scheitern kann: ein Zyklus
   * im Abhängigkeitsgraphen oder eine fehlende Berechtigung bricht den Lauf ab, bevor eine einzige
   * Definition lief, und auch das gehört in die Zeile.
   */
  private void runWithHistory(ETLRunHistory run, LocalDateRange dateRange, Supplier<List<String>> etlNames) {
    var executed = new ArrayList<String>();
    var failed = new ArrayList<String>();
    try {
      for (String etlName : etlNames.get()) {
        if (!executeETL(etlName, dateRange)) {
          failed.add(etlName);
        }
        executed.add(etlName);
      }
    } catch (RuntimeException e) {
      log.error("ETL run aborted after {} definition(s)", executed.size(), e);
      finishRun(run, FAILED, "Lauf abgebrochen nach %d Definition(en): %s".formatted(executed.size(), e));
      throw e;
    }

    if (failed.isEmpty()) {
      finishRun(run, SUCCEEDED, "%d Definition(en) ausgeführt: %s"
          .formatted(executed.size(), String.join(", ", executed)));
    } else {
      finishRun(run, FAILED, "%d Definition(en) ausgeführt: %s — fehlgeschlagen: %s"
          .formatted(executed.size(), String.join(", ", executed), String.join(", ", failed)));
    }
  }

  /**
   * Schreibt das Ergebnis in die Zeile des Laufs — und überschreibt dabei nicht, was inzwischen von
   * Hand daran geändert wurde.
   *
   * <p>Zwischen dem Start und dem Ende liegen Minuten, und genau in diesem Fenster greift
   * {@code ETLRunHistoryService.markFinished}: jemand hat den Lauf für abgestürzt erklärt, während er
   * in Wahrheit noch lief. Die Zeile wird deshalb frisch gelesen, und steht sie nicht mehr auf
   * {@code RUNNING}, bleibt diese Entscheidung stehen — der tatsächliche Ausgang kommt als Zusatz in
   * die Meldung. Beides gehört festgehalten: dass jemand eingegriffen hat, und wie der Lauf
   * ausgegangen ist.
   */
  private void finishRun(ETLRunHistory run, Status status, String message) {
    var current = runHistoryRepo.findById(run.getId()).orElse(run);
    if (current.getStatus() != RUNNING) {
      log.warn("ETL run {} was marked finished by hand while it was still running", current.getId());
      current.setMessage(shortened(
          "%s\nDer Lauf kam danach noch zu Ende: %s".formatted(current.getMessage(), message)));
      runHistoryRepo.save(current);
      return;
    }
    current.setFinishedAt(DateTimeUtils.now());
    current.setStatus(status);
    current.setMessage(shortened(message));
    runHistoryRepo.save(current);
  }

  private static String shortened(String message) {
    return message.length() > MESSAGE_MAX_LENGTH ? message.substring(0, MESSAGE_MAX_LENGTH) : message;
  }

  /**
   * Die Ausführungsreihenfolge für die genannten Einstiegspunkte samt ihrer transitiven
   * Abhängigkeiten (#1071).
   *
   * <p>Jede Definition kommt genau einmal vor, auch wenn mehrere sie brauchen: {@code visited} ist
   * über alle Einstiegspunkte hinweg dasselbe.
   */
  private List<String> executionOrderFor(Collection<String> entryPoints) {
    var graph = dependencyGraph();
    List<String> executionOrder = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    Set<String> temp = new HashSet<>();
    for (String entryPoint : entryPoints) {
      topologicalSort(entryPoint, graph, temp, visited, executionOrder);
    }
    return executionOrder;
  }

  /**
   * Ob die anfragende Person diese Definition ausführen darf.
   *
   * @throws InvalidDataException wenn es die Definition nicht gibt
   * @throws AuthorizationException wenn die Berechtigung fehlt
   */
  private void checkExecutable(String etlName) {
    var definition = definitionRepo.findByName(etlName)
        .orElseThrow(() -> new InvalidDataException(ETL_DEFINITION_NOT_FOUND, etlName));
    if (!authorization.isAuthorized(definition, AccessLevel.EXECUTE)) {
      throw new AuthorizationException(AA_NOT_ATHORIZED);
    }
  }

  /**
   * Führt eine Definition aus.
   *
   * <p>Weder Berechtigung noch Zeitraum werden hier geprüft: beides ist gefallen, bevor der Lauf
   * entstand — die Berechtigung an den öffentlichen Einstiegen, der Zeitraum in {@link #startRun}.
   * Das ist der Sinn der Trennung: „bevor ein Lauf entsteht" lässt sich nicht zusagen, wenn die
   * Prüfung mitten im Lauf sitzt (#1071).
   *
   * @return {@code true}, wenn jede Referenzperiode dieser Definition durchlief
   */
  private boolean executeETL(String etlName, LocalDateRange dateRange) {
    ETLDefinition def = definitionRepo.findByName(etlName)
        .orElseThrow(() -> new IllegalArgumentException("ETL not found: " + etlName));

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
