package org.tb.etl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tb.auth.domain.AccessLevel.EXECUTE;
import static org.tb.common.exception.ErrorCode.ETL_CYCLIC_DEPENDENCY;
import static org.tb.common.exception.ErrorCode.ETL_INVALID_DATE_RANGE;
import static org.tb.common.exception.ErrorCode.ETL_NO_EXECUTABLE_DEFINITION;
import static org.tb.common.exception.ErrorCode.ETL_RUN_ALREADY_RUNNING;
import static org.tb.etl.domain.ETLRunHistory.Status.RUNNING;
import static org.tb.etl.domain.ETLRunHistory.Trigger.MANUAL;
import static org.tb.etl.domain.ETLRunHistory.Trigger.SCHEDULED;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.common.LocalDateRange;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.etl.auth.ETLAuthorization;
import org.tb.etl.domain.ETLDefinition;
import org.tb.etl.domain.ETLDefinition.ReferencePeriod;
import org.tb.etl.domain.ETLDefinitionOption;
import org.tb.etl.domain.ETLRunHistory;
import org.tb.etl.domain.ETLRunHistory.Status;
import org.tb.etl.domain.ETLRunHistory.Trigger;
import org.tb.etl.domain.SqlStatements;
import org.tb.etl.persistence.ETLDefinitionRepository;
import org.tb.etl.persistence.ETLExecutionHistoryRepository;
import org.tb.etl.persistence.ETLRunHistoryRepository;
import org.tb.etl.service.SchemaDiffService.Diff;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class ETLServiceTest {

  private static final LocalDateRange ONE_MONTH =
      new LocalDateRange(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 20));

  /** Von nach Bis — der Zeitraum, den {@code startRun} abweist, bevor eine Zeile entsteht. */
  private static final LocalDateRange BACKWARDS =
      new LocalDateRange(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 2));

  @Mock
  private ETLDefinitionRepository definitionRepo;

  @Mock
  private ETLExecutionHistoryRepository historyRepo;

  @Mock
  private ETLRunHistoryRepository runHistoryRepo;

  @Mock
  private ParameterResolver parameterResolver;

  @Mock
  private JdbcTemplate jdbcTemplate;

  @Mock
  private ETLAuthorization authorization;

  @Mock
  private SchemaDiffService schemaDiffService;

  @InjectMocks
  private ETLService etlService;

  /**
   * Der Lauf schreibt dieselbe Instanz zweimal — beim Start und am Ende. Ein
   * {@code ArgumentCaptor} hielte beide Male die bereits fortgeschriebene Zeile fest, deshalb wird
   * bei jedem Speichern eine Kopie des Zustands abgelegt.
   */
  private final List<RunState> savedRuns = new ArrayList<>();

  /**
   * Die Identitäten der gespeicherten Zeilen, in der Reihenfolge des Speicherns. Ein Lauf darf
   * <em>eine</em> Zeile hinterlassen, nicht eine pro mitgezogener Definition — und eine fortge-
   * schriebene Zeile ist dieselbe Instanz, keine zweite (#1071).
   */
  private final List<ETLRunHistory> savedInstances = new ArrayList<>();

  private record RunState(Status status, Trigger triggeredBy, LocalDateTime startedAt,
                          LocalDateTime finishedAt, String message) {}

  @BeforeEach
  void setUp() {
    lenient().when(runHistoryRepo.save(any())).thenAnswer(invocation -> {
      ETLRunHistory run = invocation.getArgument(0);
      savedRuns.add(new RunState(run.getStatus(), run.getTriggeredBy(), run.getStartedAt(),
          run.getFinishedAt(), run.getMessage()));
      savedInstances.add(run);
      return run;
    });
    // Einmal und nicht je Definition: ein zweites when() auf denselben Aufruf löste die bereits
    // hinterlegte Antwort mit null-Argumenten aus.
    lenient().when(parameterResolver.resolve(anyString(), any())).thenAnswer(i -> i.getArgument(0));
    lenient().when(schemaDiffService.diffAround(any(), anyString())).thenAnswer(invocation -> {
      invocation.getArgument(0, Runnable.class).run();
      return new Diff(List.of(), List.of(), List.of());
    });
    // Jeder öffentliche Einstieg fragt zuerst, ob die Anmeldung überhaupt einen ETL ausführen darf —
    // und zwar bevor eine Zeile entsteht, denn die Zeile ist die Sperre. Wo es um diese Frage selbst
    // geht, überschreiben die Tests die Antwort.
    lenient().when(authorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);
  }

  @Test
  void a_run_is_recorded_when_it_starts_and_again_when_it_ends() {
    givenDefinition("worked-hours");

    etlService.execute(ONE_MONTH, List.of("worked-hours"), SCHEDULED);

    assertThat(savedRuns).hasSize(2);
    assertThat(savedRuns.getFirst().status()).isEqualTo(Status.RUNNING);
    assertThat(savedRuns.getFirst().startedAt()).isNotNull();
    assertThat(savedRuns.getFirst().finishedAt()).isNull();
    assertThat(savedRuns.getLast().status()).isEqualTo(Status.SUCCEEDED);
    assertThat(savedRuns.getLast().finishedAt()).isNotNull();
    assertThat(savedRuns.getLast().message()).contains("1 Definition(en) ausgeführt");
  }

  /**
   * Bis #1071 entschied ein {@code boolean scheduled} zugleich über den Auslöser und darüber, ob die
   * Rechteprüfung stattfindet. Der Auslöser kommt jetzt als eigener Wert herein — und genau der
   * steht in der Zeile.
   */
  @ParameterizedTest
  @EnumSource(Trigger.class)
  void a_scheduled_run_is_distinguishable_from_a_manual_one(Trigger trigger) {
    givenDefinition("worked-hours");

    etlService.execute(ONE_MONTH, List.of("worked-hours"), trigger);

    assertThat(savedRuns).isNotEmpty();
    assertThat(savedRuns).extracting(RunState::triggeredBy).containsOnly(trigger);
  }

  @Test
  void a_failing_definition_marks_the_run_as_failed_and_names_it() {
    givenDefinition("worked-hours");
    when(jdbcTemplate.update(anyString())).thenThrow(new DataAccessResourceFailureException("no connection"));

    etlService.execute(ONE_MONTH, List.of("worked-hours"), SCHEDULED);

    assertThat(savedRuns.getLast().status()).isEqualTo(Status.FAILED);
    assertThat(savedRuns.getLast().message()).contains("worked-hours");
    assertThat(savedRuns.getLast().finishedAt()).isNotNull();
  }

  @Test
  void an_unexpected_exception_aborts_the_run_and_lands_in_its_entry() {
    givenDefinition("worked-hours");
    when(parameterResolver.resolve(anyString(), any())).thenThrow(new IllegalStateException("unknown parameter"));

    assertThatThrownBy(() -> etlService.execute(ONE_MONTH, List.of("worked-hours"), SCHEDULED))
        .isInstanceOf(IllegalStateException.class);

    assertThat(savedRuns.getLast().status()).isEqualTo(Status.FAILED);
    assertThat(savedRuns.getLast().message())
        .contains("Lauf abgebrochen")
        .contains("unknown parameter");
  }

  @Test
  void a_run_that_cannot_even_determine_its_order_is_recorded_as_failed() {
    var cyclic = new ETLDefinition();
    cyclic.setName("a");
    cyclic.setDependencies(Set.of("a"));
    when(definitionRepo.findAll()).thenReturn(List.of(cyclic));

    assertThatThrownBy(() -> etlService.executeAll(ONE_MONTH, SCHEDULED))
        .isInstanceOf(IllegalStateException.class);

    assertThat(savedRuns).hasSize(2);
    assertThat(savedRuns.getLast().status()).isEqualTo(Status.FAILED);
    assertThat(savedRuns.getLast().message()).contains("Lauf abgebrochen nach 0 Definition(en)");
  }

  // --- Die Sperre und die eine Zeile pro Anstoß (#1071) --------------------------------------

  @Test
  void a_start_leaves_exactly_one_entry_in_the_run_history() {
    givenDefinition("base");
    givenDefinition("report");

    etlService.execute(ONE_MONTH, List.of("base", "report"), MANUAL);

    // Zwei Definitionen, zwei Schreibvorgänge — aber beide an derselben Zeile: einmal beim Start,
    // einmal beim Fortschreiben am Ende. Pro Definition steht eine Zeile in etl_execution_history,
    // nicht in etl_run_history.
    assertThat(savedInstances).hasSize(2);
    assertThat(savedInstances.getLast()).isSameAs(savedInstances.getFirst());
    assertThat(savedRuns.getFirst().status()).isEqualTo(Status.RUNNING);
    assertThat(savedRuns.getLast().status()).isEqualTo(Status.SUCCEEDED);
    assertThat(savedRuns.getLast().message()).contains("2 Definition(en) ausgeführt");
  }

  @Test
  void the_opened_run_carries_the_chosen_period_and_trigger_and_has_no_end_yet() {
    var run = etlService.startRun(ONE_MONTH, MANUAL);

    assertThat(savedInstances).hasSize(1);
    assertThat(run.getStatus()).isEqualTo(RUNNING);
    assertThat(run.getTriggeredBy()).isEqualTo(MANUAL);
    assertThat(run.getDateFrom()).isEqualTo(ONE_MONTH.getFrom());
    assertThat(run.getDateUntil()).isEqualTo(ONE_MONTH.getUntil());
    assertThat(run.getFinishedAt()).isNull();
  }

  @Test
  void a_second_start_while_a_run_is_going_leaves_no_second_entry() {
    var running = ETLRunHistory.builder()
        .startedAt(LocalDateTime.of(2026, 9, 24, 14, 3, 11))
        .status(RUNNING)
        .triggeredBy(MANUAL)
        .build();
    when(runHistoryRepo.findFirstByStatusOrderByStartedAtDesc(RUNNING)).thenReturn(Optional.of(running));

    assertThatThrownBy(() -> etlService.startRun(ONE_MONTH, MANUAL))
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(ex -> assertThat(errorCodesOf(ex)).containsExactly(ETL_RUN_ALREADY_RUNNING));

    // Die RUNNING-Zeile ist die Sperre: kommt der zweite Anstoß an ihr vorbei, gäbe es zwei Läufe
    // auf denselben Zieltabellen. Deshalb wird hier nicht nur nichts ausgeführt, sondern auch
    // nichts geschrieben.
    verify(runHistoryRepo, never()).save(any());
  }

  @Test
  void the_refusal_names_the_start_of_the_run_that_blocks() {
    // Ohne den Zeitpunkt liest sich „es läuft schon einer" wie eine Sackgasse — er ist der Anhalt
    // dafür, ob der Lauf plausibel noch läuft oder in Wahrheit abgestürzt ist.
    var running = ETLRunHistory.builder()
        .startedAt(LocalDateTime.of(2026, 9, 24, 14, 3, 11))
        .status(RUNNING)
        .build();
    when(runHistoryRepo.findFirstByStatusOrderByStartedAtDesc(RUNNING)).thenReturn(Optional.of(running));

    assertThatThrownBy(() -> etlService.startRun(ONE_MONTH, SCHEDULED))
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(ex -> assertThat(((ErrorCodeException) ex).getMessages().getFirst().getArguments())
            .containsExactly("24.09.2026 14:03:11"));
  }

  @Test
  void a_backwards_period_is_refused_before_a_run_comes_into_existence() {
    assertThatThrownBy(() -> etlService.startRun(BACKWARDS, MANUAL))
        .isInstanceOf(InvalidDataException.class)
        .satisfies(ex -> assertThat(errorCodesOf(ex)).containsExactly(ETL_INVALID_DATE_RANGE));

    verify(runHistoryRepo, never()).save(any());
  }

  @Test
  void a_backwards_period_is_refused_before_the_manual_run_is_even_resolved() {
    assertThatThrownBy(() -> etlService.resolveManualRun(BACKWARDS, "report"))
        .isInstanceOf(InvalidDataException.class)
        .satisfies(ex -> assertThat(errorCodesOf(ex)).containsExactly(ETL_INVALID_DATE_RANGE));

    verify(runHistoryRepo, never()).save(any());
    verify(definitionRepo, never()).findByName(anyString());
  }

  @Test
  void a_skipped_run_is_recorded_as_begun_and_ended_at_once() {
    // Der nächtliche Lauf, der gar nicht erst begann: ohne diese Zeile wäre sein Ausfall von einer
    // abgeschalteten Anwendung nicht zu unterscheiden.
    etlService.recordSkippedRun(ONE_MONTH, SCHEDULED, "Übersprungen: es lief bereits ein Lauf.");

    assertThat(savedRuns).hasSize(1);
    assertThat(savedRuns.getFirst().status()).isEqualTo(Status.SKIPPED);
    assertThat(savedRuns.getFirst().triggeredBy()).isEqualTo(SCHEDULED);
    assertThat(savedRuns.getFirst().finishedAt()).isNotNull();
    assertThat(savedRuns.getFirst().message()).contains("bereits ein Lauf");
  }

  // --- Eine einzelne Definition zieht ihre Abhängigkeiten mit (#1071) ------------------------

  @Test
  void a_single_definition_pulls_its_dependencies_along_in_topological_order() {
    // hours und costs brauchen beide base. Genau daran zeigt sich, dass base einmal läuft und
    // nicht zweimal.
    givenGraph();
    when(authorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);
    givenExecutable("report");

    var order = etlService.resolveManualRun(ONE_MONTH, "report");

    assertThat(order).containsExactlyInAnyOrder("base", "hours", "costs", "report");
    assertThat(order).doesNotHaveDuplicates();
    assertThat(order.indexOf("base")).isLessThan(order.indexOf("hours"));
    assertThat(order.indexOf("base")).isLessThan(order.indexOf("costs"));
    assertThat(order.indexOf("hours")).isLessThan(order.indexOf("report"));
    assertThat(order.indexOf("costs")).isLessThan(order.indexOf("report"));
  }

  @Test
  void only_the_chosen_definition_is_checked_not_the_dependencies_it_pulls_along() {
    // Eine Regel für „report" ohne das, was report braucht, wäre wertlos — der Lauf bräche an der
    // ersten Abhängigkeit ab. Deshalb wird der Einstieg geprüft, nicht die Hülle. Das ist die
    // Entscheidung aus #1071 und nicht ein übersehener Zweig; dieser Test hält sie fest.
    givenGraph();
    when(authorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);
    givenExecutable("report");

    var order = etlService.resolveManualRun(ONE_MONTH, "report");

    assertThat(order).contains("report", "base");
    verify(authorization, never()).isAuthorized(argThat(named("base")), any());
    verify(authorization, never()).isAuthorized(argThat(named("hours")), any());
  }

  @Test
  void a_login_without_any_etl_rule_gets_no_manual_run_at_all() {
    when(authorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(false);

    assertThatThrownBy(() -> etlService.resolveManualRun(ONE_MONTH, null))
        .isInstanceOf(AuthorizationException.class);

    verify(runHistoryRepo, never()).save(any());
  }

  @Test
  void a_rule_for_a_single_definition_is_not_enough_for_another_one() {
    givenGraph();
    when(authorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);
    givenExecutable("report");

    assertThatThrownBy(() -> etlService.resolveManualRun(ONE_MONTH, "hours"))
        .isInstanceOf(AuthorizationException.class);
  }

  @Test
  void all_definitions_means_all_the_login_may_execute() {
    givenGraph();
    when(authorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);
    givenExecutable("hours");

    var order = etlService.resolveManualRun(ONE_MONTH, null);

    // costs und report bleiben draußen; base kommt trotzdem mit, weil hours es braucht.
    assertThat(order).containsExactly("base", "hours");
  }

  @Test
  void a_login_that_may_execute_nothing_is_told_so_instead_of_starting_an_empty_run() {
    givenGraph();
    when(authorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);

    assertThatThrownBy(() -> etlService.resolveManualRun(ONE_MONTH, null))
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(ex -> assertThat(errorCodesOf(ex)).containsExactly(ETL_NO_EXECUTABLE_DEFINITION));

    verify(runHistoryRepo, never()).save(any());
  }

  /**
   * Die Zeile ist die Sperre — also darf sie nicht entstehen, bevor feststeht, dass die Anmeldung
   * überhaupt etwas ausführen darf. Sonst könnte über die REST-Schnittstelle, die nur nach
   * Authentifizierung fragt, jede beliebige Anmeldung reihenweise Sperrzeilen erzeugen und damit
   * berechtigte Läufe und den nächtlichen Lauf abweisen lassen.
   */
  @Test
  void a_login_without_any_etl_rule_leaves_no_blocking_entry_behind() {
    when(authorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(false);

    assertThatThrownBy(() -> etlService.executeAll(ONE_MONTH, MANUAL))
        .isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> etlService.execute(ONE_MONTH, List.of("base"), MANUAL))
        .isInstanceOf(AuthorizationException.class);

    verify(runHistoryRepo, never()).save(any());
  }

  /**
   * Auf allen anderen Wegen wird die Reihenfolge erst im Lauf ermittelt, ein Zyklus landet also in
   * dessen Zeile. Beim Anstoßen aus der Oberfläche wird sie vorher gebraucht — da gibt es noch
   * keine Zeile, in die er könnte, und eine durchgereichte {@code IllegalStateException} wäre eine
   * Fehlerseite statt einer Auskunft.
   */
  @Test
  void a_cycle_is_reported_to_the_requester_instead_of_ending_on_an_error_page() {
    var a = definition("a", "b");
    var b = definition("b", "a");
    when(definitionRepo.findAll()).thenReturn(List.of(a, b));
    when(definitionRepo.findByName("a")).thenReturn(Optional.of(a));
    when(authorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(true);
    when(authorization.isAuthorized(any(), any())).thenReturn(true);

    assertThatThrownBy(() -> etlService.resolveManualRun(ONE_MONTH, "a"))
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(ex -> assertThat(errorCodesOf(ex)).containsExactly(ETL_CYCLIC_DEPENDENCY));

    verify(runHistoryRepo, never()).save(any());
  }

  /**
   * Der Knopf „als beendet markieren" hält den Lauf nicht an — er markiert nur die Zeile. Kommt der
   * Lauf danach doch noch zu Ende, darf er die Entscheidung nicht stillschweigend zurücknehmen:
   * beides gehört festgehalten, dass jemand eingegriffen hat und wie der Lauf ausging.
   */
  @Test
  void a_run_that_was_marked_finished_by_hand_is_not_overwritten_when_it_ends_after_all() {
    givenDefinition("worked-hours");
    var markedByHand = ETLRunHistory.builder()
        .id(7L)
        .startedAt(LocalDateTime.of(2026, 9, 24, 10, 0, 0))
        .finishedAt(LocalDateTime.of(2026, 9, 24, 10, 5, 0))
        .status(Status.FAILED)
        .triggeredBy(MANUAL)
        .message("Von Hand als beendet markiert.")
        .build();
    when(runHistoryRepo.findById(7L)).thenReturn(Optional.of(markedByHand));

    etlService.continueRun(7L, ONE_MONTH, List.of("worked-hours"));

    assertThat(markedByHand.getStatus()).isEqualTo(Status.FAILED);
    assertThat(markedByHand.getFinishedAt()).isEqualTo(LocalDateTime.of(2026, 9, 24, 10, 5, 0));
    assertThat(markedByHand.getMessage())
        .contains("Von Hand als beendet markiert.")
        .contains("kam danach noch zu Ende");
  }

  @Test
  void the_choice_list_holds_only_what_the_login_may_execute_sorted_by_name() {
    givenGraph();
    givenExecutable("base", "hours");

    assertThat(etlService.getExecutableDefinitions())
        .extracting(ETLDefinitionOption::name)
        .containsExactly("base", "hours");
  }

  // --- Hilfsmittel ---------------------------------------------------------------------------

  /** Die Definitionen, für die diese Anmeldung eine Regel {@code ETL}/{@code EXECUTE} hat. */
  private void givenExecutable(String... names) {
    var allowed = Set.of(names);
    when(authorization.isAuthorized(any(), eq(EXECUTE)))
        .thenAnswer(i -> allowed.contains(i.getArgument(0, ETLDefinition.class).getName()));
  }

  private static ArgumentMatcher<ETLDefinition> named(String name) {
    return definition -> definition != null && name.equals(definition.getName());
  }

  private static List<ErrorCode> errorCodesOf(Throwable ex) {
    return ((ErrorCodeException) ex).getMessages().stream()
        .map(ServiceFeedbackMessage::getErrorCode)
        .toList();
  }

  /**
   * Ein Graph, in dem zwei Definitionen dieselbe Abhängigkeit nennen:
   * {@code report → {hours, costs} → base}.
   */
  private void givenGraph() {
    var base = definition("base");
    var hours = definition("hours", "base");
    var costs = definition("costs", "base");
    var report = definition("report", "hours", "costs");
    var all = List.of(base, costs, hours, report);
    lenient().when(definitionRepo.findAll()).thenReturn(all);
    all.forEach(def ->
        lenient().when(definitionRepo.findByName(def.getName())).thenReturn(Optional.of(def)));
  }

  private static ETLDefinition definition(String name, String... dependencies) {
    var definition = new ETLDefinition();
    definition.setName(name);
    definition.setDescription(name + " description");
    definition.setDependencies(Set.of(dependencies));
    return definition;
  }

  private void givenDefinition(String name) {
    var definition = new ETLDefinition();
    ReflectionTestUtils.setField(definition, "id", 42L);
    definition.setName(name);
    definition.setReferencePeriod(ReferencePeriod.MONTH);
    definition.setInit(new SqlStatements(List.of()));
    definition.setExecute(new SqlStatements(List.of("insert into target select 1")));
    definition.setCleanup(new SqlStatements(List.of()));
    when(definitionRepo.findByName(name)).thenReturn(Optional.of(definition));
    // Seit #1071 prüft jeder öffentliche Einstieg die Berechtigung selbst — executeETL tut es
    // nicht mehr. Vorher übersprang scheduled=true die Prüfung, und die Tests kamen ohne aus.
    lenient().when(authorization.isAuthorized(any(), any())).thenReturn(true);
  }

  /**
   * Tests the `calculateExecutionOrder` method of the ETLService class. This method determines the correct execution
   * order of ETL tasks based on their dependencies.
   */

  @Test
  void testCalculateExecutionOrder_ValidGraph() {
    // Arrange
    Map<String, Set<String>> graph = new HashMap<>();
    graph.put("TaskA", Set.of("TaskB", "TaskC"));
    graph.put("TaskB", Set.of("TaskC"));
    graph.put("TaskC", Collections.emptySet());
    graph.put("TaskD", Set.of("TaskE"));
    graph.put("TaskE", Set.of("TaskB", "TaskC", "TaskA"));

    // Act
    List<String> executionOrder = etlService.calculateExecutionOrder(graph);

    // Assert
    assertEquals(List.of("TaskC", "TaskB", "TaskA", "TaskE", "TaskD"), executionOrder);
  }

  @Test
  void testCalculateExecutionOrder_SingleNode() {
    // Arrange
    Map<String, Set<String>> graph = new HashMap<>();
    graph.put("TaskA", Collections.emptySet());

    // Act
    List<String> executionOrder = etlService.calculateExecutionOrder(graph);

    // Assert
    assertEquals(List.of("TaskA"), executionOrder);
  }

  @Test
  void testCalculateExecutionOrder_DisconnectedGraph() {
    // Arrange
    Map<String, Set<String>> graph = new HashMap<>();
    graph.put("TaskA", Collections.emptySet());
    graph.put("TaskB", Collections.emptySet());
    graph.put("TaskC", Collections.emptySet());

    // Act
    List<String> executionOrder = etlService.calculateExecutionOrder(graph);

    // Assert
    assertTrue(executionOrder.containsAll(List.of("TaskA", "TaskB", "TaskC")));
  }

  @Test
  void testCalculateExecutionOrder_CyclicDependency() {
    // Arrange
    Map<String, Set<String>> graph = new HashMap<>();
    graph.put("TaskA", Set.of("TaskB"));
    graph.put("TaskB", Set.of("TaskA"));

    // Act & Assert
    assertThrows(IllegalStateException.class, () -> etlService.calculateExecutionOrder(graph));
  }

  @Test
  void testCalculateExecutionOrder_EmptyGraph() {
    // Arrange
    Map<String, Set<String>> graph = new HashMap<>();

    // Act
    List<String> executionOrder = etlService.calculateExecutionOrder(graph);

    // Assert
    assertTrue(executionOrder.isEmpty());
  }
}
