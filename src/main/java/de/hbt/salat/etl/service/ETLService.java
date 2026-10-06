package de.hbt.salat.etl.service;

import static java.util.Comparator.comparing;
import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static de.hbt.salat.common.exception.ErrorCode.ETL_CYCLIC_DEPENDENCY;
import static de.hbt.salat.common.exception.ErrorCode.ETL_DEFINITION_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.ETL_INVALID_DATE_RANGE;
import static de.hbt.salat.common.exception.ErrorCode.ETL_NO_EXECUTABLE_DEFINITION;
import static de.hbt.salat.common.exception.ErrorCode.ETL_RUN_ALREADY_RUNNING;
import static de.hbt.salat.common.exception.ErrorCode.ETL_RUN_NOT_FOUND;
import static de.hbt.salat.etl.domain.ETLRunHistory.MESSAGE_MAX_LENGTH;
import static de.hbt.salat.etl.domain.ETLRunHistory.Status.FAILED;
import static de.hbt.salat.etl.domain.ETLRunHistory.Status.RUNNING;
import static de.hbt.salat.etl.domain.ETLRunHistory.Status.SKIPPED;
import static de.hbt.salat.etl.domain.ETLRunHistory.Status.SUCCEEDED;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.scheduling.RunFinisher;
import de.hbt.salat.common.util.DateTimeUtils;
import de.hbt.salat.etl.auth.ETLAuthorization;
import de.hbt.salat.etl.domain.ETLDefinition;
import de.hbt.salat.etl.domain.SqlStatements;
import de.hbt.salat.common.event.SignsRenamedEvent;
import de.hbt.salat.etl.domain.ETLDefinition.ReferencePeriod;
import de.hbt.salat.etl.domain.ETLDefinitionOption;
import de.hbt.salat.etl.domain.ETLExecutionHistory;
import de.hbt.salat.etl.domain.ETLRunHistory;
import de.hbt.salat.etl.domain.ETLRunHistory.Status;
import de.hbt.salat.etl.domain.ETLRunHistory.Trigger;
import de.hbt.salat.etl.persistence.ETLDefinitionRepository;
import de.hbt.salat.etl.persistence.ETLExecutionHistoryRepository;
import de.hbt.salat.etl.persistence.ETLRunHistoryRepository;
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
  private final RunFinisher runFinisher;

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
   * <p>Die Rechteprüfung steht hier selbst und nicht nur bei den Aufrufern (#1289): die Zeile ist die
   * Sperre, und eine Methode, die sie schreibt, darf sich nicht darauf verlassen, dass jeder künftige
   * Aufrufer vorher fragt.
   *
   * @throws AuthorizationException ohne ETL-Recht — geprüft vor allem anderen
   * @throws InvalidDataException wenn der Zeitraum verkehrt herum liegt — geprüft, <em>bevor</em>
   *     eine Zeile entsteht
   * @throws BusinessRuleException wenn bereits ein Lauf läuft
   */
  public synchronized ETLRunHistory startRun(LocalDateRange dateRange, Trigger trigger) {
    checkAuthorizedForAnyETL();
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
    checkAuthorizedForAnyETL();
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
      var definitions = definitionsById();
      var order = definitionsInOrder(calculateExecutionOrder(dependencyGraph(definitions.values())), definitions);
      order.forEach(this::checkExecutable);
      return order;
    });
  }

  /** Alle Definitionen nach id — sortiert, damit eine Reihenfolge nicht vom Zufall einer Hash-Map abhängt. */
  private Map<Long, ETLDefinition> definitionsById() {
    var byId = new TreeMap<Long, ETLDefinition>();
    definitionRepo.findAllWithDependencies().forEach(definition -> byId.put(definition.getId(), definition));
    return byId;
  }

  /**
   * Der Graph der Abhängigkeiten über die ids (#1207). Ein Name taucht darin nicht mehr auf: wer eine
   * Definition umbenennt, ändert weder die Reihenfolge noch, ob ein Lauf durchgeht.
   */
  private static Map<Long, Set<Long>> dependencyGraph(Collection<ETLDefinition> definitions) {
    Map<Long, Set<Long>> graph = new TreeMap<>();
    for (ETLDefinition definition : definitions) {
      graph.put(definition.getId(), definition.getDependencies().stream()
          .map(ETLDefinition::getId)
          .collect(Collectors.toSet()));
    }
    return graph;
  }

  /**
   * Die Ausführungsreihenfolge: jede Definition nach denen, von denen sie abhängt. Die Knoten werden
   * in aufsteigender id besucht, damit dieselben Definitionen immer in derselben Reihenfolge laufen.
   *
   * @throws IllegalStateException bei einem Zyklus
   */
  @VisibleForTesting
  public List<Long> calculateExecutionOrder(Map<Long, Set<Long>> graph) {
    List<Long> executionOrder = new ArrayList<>();
    Set<Long> visited = new HashSet<>();
    Set<Long> temp = new HashSet<>();

    for (Long node : new TreeMap<>(graph).keySet()) {
      if (!visited.contains(node)) {
        topologicalSort(node, graph, temp, visited, executionOrder);
      }
    }

    return executionOrder;
  }

  private void topologicalSort(Long node, Map<Long, Set<Long>> graph, Set<Long> temp, Set<Long> visited, List<Long> executionOrder) {
    if (temp.contains(node)) {
      throw new IllegalStateException("Cyclic dependency detected");
    }
    if (!visited.contains(node)) {
      temp.add(node);
      for (Long dependency : new TreeSet<>(graph.getOrDefault(node, Set.of()))) {
        topologicalSort(dependency, graph, temp, visited, executionOrder);
      }
      temp.remove(node);
      visited.add(node);
      executionOrder.add(node);
    }
  }

  /** Die Definitionen zu den ids, in deren Reihenfolge; eine id ohne Definition fällt weg. */
  private static List<ETLDefinition> definitionsInOrder(List<Long> ids, Map<Long, ETLDefinition> definitions) {
    return ids.stream().map(definitions::get).filter(Objects::nonNull).toList();
  }

  public void execute(LocalDateRange dateRange, List<String> etlNames, Trigger trigger) {
    checkAuthorizedForAnyETL();
    runWithHistory(startRun(dateRange, trigger), dateRange, () -> etlNames.stream()
        .map(this::checkExecutable)
        .toList());
  }

  /**
   * Die Vorprüfung, die vor jeder Zeile fällt — in den Einstiegen und seit #1289 auch in
   * {@link #startRun} und {@link #recordSkippedRun} selbst.
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
  /**
   * Die Namen der ETL-Definitionen, deren SQL das alte Kürzel eines umbenannten Auftrags oder
   * Unterauftrags als Literal nennt (#1206), sortiert. Umgeschrieben wird hier nichts: freies SQL
   * automatisch anzupassen ist riskanter, als die Stellen zu nennen.
   */
  @Transactional(readOnly = true)
  @Authorized(requiresManager = true)
  public List<String> getNamesOfDefinitionsMentioning(SignsRenamedEvent renamed) {
    return definitionRepo.findAll().stream()
        .filter(def -> Stream.of(def.getInit(), def.getExecute(), def.getCleanup())
            .filter(Objects::nonNull)
            .map(SqlStatements::getStatements)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .anyMatch(renamed::isMentionedIn))
        .map(ETLDefinition::getName)
        .sorted()
        .toList();
  }

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
   * @return die ids in der Reihenfolge, in der die Definitionen laufen — bei einer einzelnen samt
   *     ihrer Abhängigkeiten
   */
  public List<Long> resolveManualRun(LocalDateRange dateRange, String etlName) {
    // Die Berechtigung zuerst, die Eingabe danach: wer gar nicht anstoßen darf, soll das erfahren
    // und nicht erst eine Rückmeldung zu seiner Eingabe bekommen. Nebenbei ist das die Reihenfolge,
    // die ein Test überhaupt auseinanderhalten kann — läge die Eingabeprüfung vorn, verdeckte eine
    // ungültige Eingabe jede Aussage über das Recht.
    checkAuthorizedForAnyETL();
    if (!dateRange.isValid()) {
      throw new InvalidDataException(ETL_INVALID_DATE_RANGE);
    }

    List<Long> entryPoints;
    if (etlName != null && !etlName.isBlank()) {
      entryPoints = List.of(checkExecutable(etlName).getId());
    } else {
      entryPoints = definitionRepo.findAll().stream()
          .filter(def -> authorization.isAuthorized(def, AccessLevel.EXECUTE))
          .map(ETLDefinition::getId)
          .toList();
      if (entryPoints.isEmpty()) {
        throw new BusinessRuleException(ETL_NO_EXECUTABLE_DEFINITION);
      }
    }

    // Geprüft wird der Einstieg, nicht die Hülle: die Abhängigkeiten, die `executionOrderFor` hier
    // mitzieht, laufen ohne eigene Prüfung. Eine Regel für X ohne das, was X braucht, wäre sonst
    // wertlos — der Lauf bräche an der ersten Abhängigkeit ab (#1071).
    try {
      return executionOrderFor(entryPoints).stream().map(ETLDefinition::getId).toList();
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
   * Anfrage gefallen, wo es die anfragende Person noch gibt. Im Hintergrund käme jede Prüfung durch,
   * denn {@code AuthorizedUser#initForJob} macht den Job-Benutzer zum Manager.
   *
   * <p><b>Deshalb paketsichtbar statt öffentlich</b> (#1289): der Schutz ist, dass nur
   * {@link ETLRunLauncher} sie erreicht. Ein Controller oder ein anderes Modul, das einen eröffneten
   * Lauf mit beliebigen Definitionen fortsetzen könnte, kommt an ihr nicht vorbei — das prüft der
   * Compiler, nicht eine Laufzeitprüfung, die immer durchginge.
   */
  void continueRun(long runId, LocalDateRange dateRange, List<Long> etlIds) {
    var run = runHistoryRepo.findById(runId)
        .orElseThrow(() -> new InvalidDataException(ETL_RUN_NOT_FOUND));
    runWithHistory(run, dateRange, () -> definitionsInOrder(etlIds, definitionsById()));
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
  private void runWithHistory(ETLRunHistory run, LocalDateRange dateRange, Supplier<List<ETLDefinition>> definitions) {
    var executed = new ArrayList<String>();
    var failed = new ArrayList<String>();
    try {
      for (ETLDefinition definition : definitions.get()) {
        if (!executeETL(definition, dateRange)) {
          failed.add(definition.getName());
        }
        executed.add(definition.getName());
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
   *
   * <p>Geschrieben wird über {@link RunFinisher} (#1300): ist die Datenbank in diesem Moment nicht
   * erreichbar, kommt das Ende später nach, statt die Zeile als Sperre stehen zu lassen. Deshalb wird
   * die Zeile in jedem Versuch neu gelesen, und der Endzeitpunkt ist der des Laufs, nicht der des
   * Schreibens.
   */
  private void finishRun(ETLRunHistory run, Status status, String message) {
    var finishedAt = DateTimeUtils.now();
    runFinisher.finish("ETL run " + run.getId(), () -> {
      var current = runHistoryRepo.findById(run.getId()).orElse(run);
      if (current.getStatus() != RUNNING) {
        log.warn("ETL run {} was marked finished by hand while it was still running", current.getId());
        current.setMessage(shortened(
            "%s\nDer Lauf kam danach noch zu Ende: %s".formatted(current.getMessage(), message)));
        runHistoryRepo.save(current);
        return;
      }
      current.setFinishedAt(finishedAt);
      current.setStatus(status);
      current.setMessage(shortened(message));
      runHistoryRepo.save(current);
    });
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
  private List<ETLDefinition> executionOrderFor(Collection<Long> entryPoints) {
    var definitions = definitionsById();
    var graph = dependencyGraph(definitions.values());
    List<Long> executionOrder = new ArrayList<>();
    Set<Long> visited = new HashSet<>();
    Set<Long> temp = new HashSet<>();
    for (Long entryPoint : entryPoints) {
      topologicalSort(entryPoint, graph, temp, visited, executionOrder);
    }
    return definitionsInOrder(executionOrder, definitions);
  }

  /**
   * Ob die anfragende Person diese Definition ausführen darf — angesprochen über ihren Namen, wie es
   * die REST-Schnittstelle und das Formular tun.
   *
   * @return die Definition
   * @throws InvalidDataException wenn es die Definition nicht gibt
   * @throws AuthorizationException wenn die Berechtigung fehlt
   */
  private ETLDefinition checkExecutable(String etlName) {
    var definition = definitionRepo.findByName(etlName)
        .orElseThrow(() -> new InvalidDataException(ETL_DEFINITION_NOT_FOUND, etlName));
    return checkExecutable(definition);
  }

  /** @throws AuthorizationException wenn die Berechtigung fehlt */
  private ETLDefinition checkExecutable(ETLDefinition definition) {
    if (!authorization.isAuthorized(definition, AccessLevel.EXECUTE)) {
      throw new AuthorizationException(AA_NOT_ATHORIZED);
    }
    return definition;
  }

  /**
   * Führt eine Definition aus.
   *
   * <p>Weder Berechtigung noch Zeitraum werden hier geprüft: beides ist gefallen, bevor der Lauf
   * entstand — die Berechtigung an den öffentlichen Einstiegen, der Zeitraum in {@link #startRun}.
   * Das ist der Sinn der Trennung: „bevor ein Lauf entsteht" lässt sich nicht zusagen, wenn die
   * Prüfung mitten im Lauf sitzt (#1071).
   *
   * <p>Der Eintrag je Periode hält nur fest, was sich nicht aus Definition und Zeitraum herleiten
   * lässt (#1357): Zeitraum, Dauer und Zeilenzahl je Teil, im Fehlerfall die gescheiterte Anweisung.
   * Das SQL jeder Anweisung stand früher darin und machte rund 95 % des Eintrags aus — ein Lauf über
   * die ganze Historie schrieb so über 100 MB.
   *
   * @return {@code true}, wenn jede Referenzperiode dieser Definition durchlief
   */
  private boolean executeETL(ETLDefinition def, LocalDateRange dateRange) {
    var etlName = def.getName();

    var refPeriods = generateReferencePeriodRanges(dateRange, def.getReferencePeriod());
    boolean allPeriodsSucceeded = true;
    for (LocalDateRange refPeriod : refPeriods) {
      boolean success = false;
      StringBuilder message = new StringBuilder();
      message.append("Date Range: ").append(refPeriod).append("\n");
      var running = new RunningStatement();

      try {
        var initDiff = schemaDiffService.diffAround(
            () -> {
              var stopwatch = Stopwatch.createStarted();
              int initRows = executeStatements("init", def.getInit(), refPeriod, running);
              stopwatch.stop();
              message.append("Init took ").append(stopwatch).append(" (").append(initRows).append(" rows affected, ");
            },
            "salat"
        );
        message.append(initDiff.created().size()).append(" tables/objects created)\n");

        {
          var stopwatch = Stopwatch.createStarted();
          int executeRows = executeStatements("execute", def.getExecute(), refPeriod, running);
          stopwatch.stop();
          message.append("Execute took ").append(stopwatch).append(" (").append(executeRows).append(" rows affected)\n");
        }

        var cleanupDiff = schemaDiffService.diffAround(
            () -> {
              var stopwatch = Stopwatch.createStarted();
              int cleanupRows = executeStatements("cleanup", def.getCleanup(), refPeriod, running);
              stopwatch.stop();
              message.append("Cleanup took ").append(stopwatch).append(" (").append(cleanupRows).append(" rows affected, ");
            },
            "salat"
        );
        message.append(cleanupDiff.dropped().size()).append(" tables/objects dropped)\n");

        success = true;
      } catch (DataAccessException ex) {
        log.error("ETL execution failed: {}", etlName, ex);
        running.appendTo(message);
        message.append("ETL execution failed: ").append(ex.getMessage()).append("\n");
      } catch (RuntimeException ex) {
        // Alles jenseits des SQLs — aufgelöste Parameter, Schema-Vergleich — bricht den ganzen Lauf
        // ab, so wie bisher. Ohne diesen Zweig stünde in der Zeile kein Wort darüber, woran es lag
        // (#573).
        log.error("ETL execution failed unexpectedly: {}", etlName, ex);
        running.appendTo(message);
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

  /**
   * Setzt die Anweisungen eines Teils ab und hält dabei fest, welche gerade läuft — für die Meldung,
   * falls sie scheitert.
   *
   * @return die Summe der betroffenen Zeilen
   */
  private int executeStatements(String part, SqlStatements statements, LocalDateRange refPeriod,
      RunningStatement running) {
    int rows = 0;
    var rawStatements = statements.getStatements();
    for (int i = 0; i < rawStatements.size(); i++) {
      // Erst die rohe Anweisung: scheitert schon das Auflösen der Parameter, steht sie in der Meldung.
      running.start(part, i + 1, rawStatements.get(i));
      String sql = parameterResolver.resolve(rawStatements.get(i), refPeriod);
      running.start(part, i + 1, sql);
      log.debug("Send {} SQL: {}", part, sql);
      rows += jdbc.update(sql);
    }
    running.finish();
    return rows;
  }

  /**
   * Die Anweisung, die gerade läuft (#1357). Im Erfolgsfall kommt sie nicht in die Meldung — nur
   * wenn eine Ausnahme sie unterbricht, steht sie samt Teil und Position darin.
   */
  private static final class RunningStatement {
    private String part;
    private int position;
    private String sql;

    void start(String part, int position, String sql) {
      this.part = part;
      this.position = position;
      this.sql = sql;
    }

    void finish() {
      sql = null;
    }

    void appendTo(StringBuilder message) {
      if (sql != null) {
        message.append("Failed statement (").append(part).append(", statement ").append(position).append("): ")
            .append(sql).append("\n");
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

  /**
   * Ob es die Definition gibt — nur für eine Anmeldung mit ETL-Recht (#1289). Ohne die Prüfung davor
   * verriete die Antwort der REST-Schnittstelle jeder Anmeldung, welche Definitionsnamen es gibt:
   * 404 für einen unbekannten Namen, 403 für einen bekannten.
   */
  public boolean isETLExisting(String etlName) {
    checkAuthorizedForAnyETL();
    return definitionRepo.findByName(etlName).isPresent();
  }

}
