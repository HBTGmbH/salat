package de.hbt.salat.etl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.auth.domain.AccessLevel.EXECUTE;
import static de.hbt.salat.common.exception.ErrorCode.ETL_CYCLIC_DEPENDENCY;
import static de.hbt.salat.common.exception.ErrorCode.ETL_INVALID_DATE_RANGE;
import static de.hbt.salat.common.exception.ErrorCode.ETL_NO_EXECUTABLE_DEFINITION;
import static de.hbt.salat.common.exception.ErrorCode.ETL_RUN_ALREADY_RUNNING;
import static de.hbt.salat.etl.domain.ETLRunHistory.Status.RUNNING;
import static de.hbt.salat.etl.domain.ETLRunHistory.Trigger.MANUAL;
import static de.hbt.salat.etl.domain.ETLRunHistory.Trigger.SCHEDULED;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.CannotCreateTransactionException;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.scheduling.ManualTaskScheduler;
import de.hbt.salat.common.scheduling.RunFinisher;
import de.hbt.salat.etl.auth.ETLAuthorization;
import de.hbt.salat.etl.domain.ETLDefinition;
import de.hbt.salat.etl.domain.ETLDefinition.ReferencePeriod;
import de.hbt.salat.etl.domain.ETLDefinitionOption;
import de.hbt.salat.etl.domain.ETLExecutionHistory;
import de.hbt.salat.etl.domain.ETLRunHistory;
import de.hbt.salat.etl.domain.ETLRunHistory.Status;
import de.hbt.salat.etl.domain.ETLRunHistory.Trigger;
import de.hbt.salat.etl.domain.SqlStatements;
import de.hbt.salat.etl.persistence.ETLDefinitionRepository;
import de.hbt.salat.etl.persistence.ETLExecutionHistoryRepository;
import de.hbt.salat.etl.persistence.ETLRunHistoryRepository;
import de.hbt.salat.etl.service.SchemaDiffService.Diff;
import de.hbt.salat.etl.service.SchemaDiffService.SchemaObject;

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

  /** Plans the retries of {@link RunFinisher}; the test runs them (#1300). */
  private final ManualTaskScheduler taskScheduler =
      new ManualTaskScheduler(Instant.parse("2026-10-03T00:01:00Z"));

  @Spy
  private RunFinisher runFinisher = taskScheduler.runFinisher(Duration.ofMinutes(60));

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

  /** Die Einträge der Ausführungshistorie, in der Reihenfolge des Speicherns. */
  private final List<ETLExecutionHistory> savedEntries = new ArrayList<>();

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
      return new Diff(List.of(), List.of());
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
   * Vom Schema-Vergleich zählt nur die Anzahl: was init anlegt und was cleanup entfernt (#1356).
   * Der Vergleich liefert hier für beide Teile dasselbe Ergebnis — welche Liste je Teil gelesen
   * wird, zeigt die Zahl.
   */
  @Test
  void the_execution_entry_counts_what_init_created_and_cleanup_dropped() {
    givenDefinition("worked-hours");
    doAnswer(invocation -> {
      invocation.getArgument(0, Runnable.class).run();
      return new Diff(
          List.of(new SchemaObject("TABLE", "tmp_a"), new SchemaObject("TABLE", "tmp_b")),
          List.of(new SchemaObject("TABLE", "tmp_c")));
    }).when(schemaDiffService).diffAround(any(), anyString());
    var entries = new ArrayList<ETLExecutionHistory>();
    when(historyRepo.save(any())).thenAnswer(i -> {
      entries.add(i.getArgument(0));
      return i.getArgument(0);
    });

    etlService.execute(ONE_MONTH, List.of("worked-hours"), SCHEDULED);

    assertThat(entries).singleElement().satisfies(entry -> assertThat(entry.getMessage())
        .contains("rows affected, 2 tables/objects created)\n")
        .contains("rows affected, 1 tables/objects dropped)\n"));
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
    var cyclic = definition("a", "a");
    when(definitionRepo.findAllWithDependencies()).thenReturn(List.of(cyclic));

    assertThatThrownBy(() -> etlService.executeAll(ONE_MONTH, SCHEDULED))
        .isInstanceOf(IllegalStateException.class);

    assertThat(savedRuns).hasSize(2);
    assertThat(savedRuns.getLast().status()).isEqualTo(Status.FAILED);
    assertThat(savedRuns.getLast().message()).contains("Lauf abgebrochen nach 0 Definition(en)");
  }

  // --- Der Eintrag je Periode (#1357) ------------------------------------------------------------

  @Test
  void a_successful_entry_holds_period_durations_and_counts_but_no_sql() {
    givenThreePartDefinition();
    when(jdbcTemplate.update(anyString())).thenReturn(3);

    etlService.execute(ONE_MONTH, List.of("worked-hours"), SCHEDULED);

    var entry = savedEntries.getFirst();
    assertThat(entry.isSuccess()).isTrue();
    assertThat(entry.getMessage())
        .doesNotContain("Init SQL", "Execute SQL", "Cleanup SQL", "create table", "insert into", "drop table")
        .startsWith("Date Range: 2026-03-01 - 2026-03-31\n")
        .containsPattern("\nInit took \\S+ \\S+ \\(3 rows affected, 0 tables/objects created\\)\n")
        .containsPattern("\nExecute took \\S+ \\S+ \\(6 rows affected\\)\n")
        .containsPattern("\nCleanup took \\S+ \\S+ \\(3 rows affected, 0 tables/objects dropped\\)\n$");
  }

  @ParameterizedTest
  @CsvSource({
      "init,    1, create table tmp_hours as select 1",
      "execute, 2, insert into target select 2",
      "cleanup, 1, drop table tmp_hours",
  })
  void a_failed_entry_names_the_statement_its_part_and_the_error(String part, int position, String failing) {
    givenThreePartDefinition();
    when(jdbcTemplate.update(anyString())).thenAnswer(i -> {
      if (failing.equals(i.getArgument(0))) {
        throw new DataAccessResourceFailureException("table is locked");
      }
      return 1;
    });

    etlService.execute(ONE_MONTH, List.of("worked-hours"), SCHEDULED);

    var entry = savedEntries.getFirst();
    assertThat(entry.isSuccess()).isFalse();
    assertThat(entry.getMessage())
        .contains("Failed statement (" + part + ", statement " + position + "): " + failing + "\n")
        .contains("ETL execution failed: table is locked\n")
        .doesNotContain("Init SQL", "Execute SQL", "Cleanup SQL");
  }

  /** Scheitert schon das Auflösen der Parameter, steht die Anweisung so in der Meldung, wie sie definiert ist. */
  @Test
  void a_statement_whose_parameters_cannot_be_resolved_is_named_as_defined() {
    givenThreePartDefinition();
    when(parameterResolver.resolve(anyString(), any())).thenAnswer(i -> {
      if (i.getArgument(0, String.class).contains(":unknown")) {
        throw new IllegalStateException("unknown parameter");
      }
      return i.getArgument(0);
    });
    definitionRepo.findByName("worked-hours").orElseThrow()
        .setExecute(new SqlStatements(List.of("insert into target select :unknown")));

    assertThatThrownBy(() -> etlService.execute(ONE_MONTH, List.of("worked-hours"), SCHEDULED))
        .isInstanceOf(IllegalStateException.class);

    assertThat(savedEntries.getFirst().getMessage())
        .contains("Failed statement (execute, statement 1): insert into target select :unknown\n")
        .contains("unknown parameter");
  }

  /** Eine Definition mit Anweisungen in allen drei Teilen, zwei davon in execute. */
  private void givenThreePartDefinition() {
    givenDefinition("worked-hours");
    var definition = definitionRepo.findByName("worked-hours").orElseThrow();
    definition.setInit(new SqlStatements(List.of("create table tmp_hours as select 1")));
    definition.setExecute(new SqlStatements(List.of("insert into target select 1", "insert into target select 2")));
    definition.setCleanup(new SqlStatements(List.of("drop table tmp_hours")));
    lenient().when(historyRepo.save(any())).thenAnswer(i -> {
      savedEntries.add(i.getArgument(0));
      return i.getArgument(0);
    });
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
  void opening_a_run_needs_an_etl_right_of_its_own() {
    // #1289: the row is the lock, so the method that writes it does not leave the check to its
    // callers - a caller without one could otherwise turn away every further run
    when(authorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(false);

    assertThatThrownBy(() -> etlService.startRun(ONE_MONTH, MANUAL)).isInstanceOf(AuthorizationException.class);

    verify(runHistoryRepo, never()).save(any());
  }

  @Test
  void the_right_is_checked_before_the_period() {
    // whoever may not start a run learns that, not something about their input
    when(authorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(false);

    assertThatThrownBy(() -> etlService.startRun(BACKWARDS, MANUAL)).isInstanceOf(AuthorizationException.class);
  }

  @Test
  void recording_a_skipped_run_needs_an_etl_right_as_well() {
    when(authorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(false);

    assertThatThrownBy(() -> etlService.recordSkippedRun(ONE_MONTH, SCHEDULED, "Übersprungen"))
        .isInstanceOf(AuthorizationException.class);

    verify(runHistoryRepo, never()).save(any());
  }

  @Test
  void whether_a_definition_exists_is_only_told_to_someone_with_an_etl_right() {
    // #1289: otherwise 404 against 403 tells any login which definition names there are
    when(authorization.isAuthorizedForAnyETL(EXECUTE)).thenReturn(false);

    assertThatThrownBy(() -> etlService.isETLExisting("worked-hours")).isInstanceOf(AuthorizationException.class);

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

    var order = namesOf(etlService.resolveManualRun(ONE_MONTH, "report"));

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

    var order = namesOf(etlService.resolveManualRun(ONE_MONTH, "report"));

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

    var order = namesOf(etlService.resolveManualRun(ONE_MONTH, null));

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
    when(definitionRepo.findAllWithDependencies()).thenReturn(List.of(a, b));
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

    etlService.continueRun(7L, ONE_MONTH, List.of(42L));

    assertThat(markedByHand.getStatus()).isEqualTo(Status.FAILED);
    assertThat(markedByHand.getFinishedAt()).isEqualTo(LocalDateTime.of(2026, 9, 24, 10, 5, 0));
    assertThat(markedByHand.getMessage())
        .contains("Von Hand als beendet markiert.")
        .contains("kam danach noch zu Ende");
  }

  /**
   * #1300: der Datenbankserver startete eine Minute nach Beginn des nächtlichen Laufs neu. Eine
   * Definition brach ab, und auch das Schreiben des Ausgangs scheiterte am Verbindungsabbruch — die
   * Zeile blieb auf {@code RUNNING} und sperrte jeden weiteren Lauf, obwohl der Prozess wusste, wie der
   * Lauf ausgegangen war.
   */
  @Test
  void a_run_whose_end_cannot_be_written_at_first_writes_it_once_the_database_is_back() {
    givenDefinition("worked-hours");
    var run = ETLRunHistory.builder()
        .id(7L)
        .startedAt(LocalDateTime.of(2026, 10, 3, 2, 0, 0))
        .status(Status.RUNNING)
        .triggeredBy(SCHEDULED)
        .build();
    when(runHistoryRepo.findById(7L))
        .thenReturn(Optional.of(run))
        .thenThrow(new CannotCreateTransactionException("Could not open JPA EntityManager for transaction",
            new SQLException("Connection is closed")))
        .thenReturn(Optional.of(run));
    when(historyRepo.save(any()))
        .thenThrow(new DataAccessResourceFailureException("Server shutdown in progress"));

    assertThatThrownBy(() -> etlService.continueRun(7L, ONE_MONTH, List.of(42L)))
        .isInstanceOf(DataAccessResourceFailureException.class);
    taskScheduler.runAllPlanned();

    assertThat(run.getStatus()).isEqualTo(Status.FAILED);
    assertThat(run.getFinishedAt()).isNotNull();
    assertThat(run.getMessage()).contains("Lauf abgebrochen").contains("Server shutdown in progress");
  }

  @Test
  void the_choice_list_holds_only_what_the_login_may_execute_sorted_by_name() {
    givenGraph();
    givenExecutable("base", "hours");

    assertThat(etlService.getExecutableDefinitions())
        .extracting(ETLDefinitionOption::name)
        .containsExactly("base", "hours");
  }

  // --- Abhängigkeiten über die id (#1207) -----------------------------------------------------

  /**
   * Die Abhängigkeiten nennen die Definition über ihre id. Wer eine Definition umbenennt, ändert
   * weder die Reihenfolge noch, ob der Lauf durchgeht — vorher scheiterte „alle Definitionen" an der
   * ersten Abhängigkeit, die noch den alten Namen nannte.
   */
  @Test
  void a_renamed_definition_leaves_the_run_of_all_definitions_in_the_same_order() {
    givenGraph();
    when(authorization.isAuthorized(any(), any())).thenReturn(true);
    var executed = new ArrayList<Long>();
    when(historyRepo.save(any())).thenAnswer(i -> {
      executed.add(i.<ETLExecutionHistory>getArgument(0).getEtlId());
      return i.getArgument(0);
    });
    givenRunnable();

    etlService.executeAll(ONE_MONTH, SCHEDULED);
    var before = List.copyOf(executed);
    executed.clear();
    definitionRepo.findAll().forEach(def -> def.setName(def.getName() + "-neu"));
    etlService.executeAll(ONE_MONTH, SCHEDULED);

    assertThat(executed).isEqualTo(before);
    assertThat(savedRuns.getLast().status()).isEqualTo(Status.SUCCEEDED);
    assertThat(savedRuns.getLast().message()).contains("base-neu", "report-neu");
  }

  /** Jede Definition des Graphen bekommt einen Abschnitt und eine Anweisung, damit sie laufen kann. */
  private void givenRunnable() {
    definitionRepo.findAll().forEach(def -> {
      def.setReferencePeriod(ReferencePeriod.MONTH);
      def.setInit(new SqlStatements(List.of()));
      def.setExecute(new SqlStatements(List.of("insert into target select 1")));
      def.setCleanup(new SqlStatements(List.of()));
    });
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

  /** Die ids der Definitionen dieser Tests — der Graph der Reihenfolge läuft über die id (#1207). */
  private static final Map<String, Long> IDS = Map.of(
      "base", 1L, "hours", 2L, "costs", 3L, "report", 4L, "a", 11L, "b", 12L);

  private static List<String> namesOf(List<Long> ids) {
    return ids.stream()
        .map(id -> IDS.entrySet().stream().filter(e -> e.getValue().equals(id)).findFirst().orElseThrow().getKey())
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
    lenient().when(definitionRepo.findAllWithDependencies()).thenReturn(all);
    all.forEach(def ->
        lenient().when(definitionRepo.findByName(def.getName())).thenReturn(Optional.of(def)));
  }

  /** Die Definitionen eines Tests nach Namen — eine Abhängigkeit ist dieselbe Entität (#1350). */
  private final Map<String, ETLDefinition> definitions = new HashMap<>();

  /**
   * Die Definition {@code name}, abhängig von den genannten. Eine Abhängigkeit, die noch nicht
   * angelegt ist, entsteht hier schon — so lässt sich auch ein Zyklus beschreiben.
   */
  private ETLDefinition definition(String name, String... dependencies) {
    var definition = definitions.computeIfAbsent(name, ETLServiceTest::newDefinition);
    Arrays.stream(dependencies)
        .map(dependency -> definitions.computeIfAbsent(dependency, ETLServiceTest::newDefinition))
        .forEach(definition.getDependencies()::add);
    return definition;
  }

  private static ETLDefinition newDefinition(String name) {
    var definition = new ETLDefinition();
    ReflectionTestUtils.setField(definition, "id", IDS.get(name));
    definition.setName(name);
    definition.setDescription(name + " description");
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
    lenient().when(definitionRepo.findByName(name)).thenReturn(Optional.of(definition));
    lenient().when(definitionRepo.findAll()).thenReturn(List.of(definition));
    lenient().when(definitionRepo.findAllWithDependencies()).thenReturn(List.of(definition));
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
    Map<Long, Set<Long>> graph = new HashMap<>();
    graph.put(1L, Set.of(2L, 3L));
    graph.put(2L, Set.of(3L));
    graph.put(3L, Collections.emptySet());
    graph.put(4L, Set.of(5L));
    graph.put(5L, Set.of(2L, 3L, 1L));

    // Act
    List<Long> executionOrder = etlService.calculateExecutionOrder(graph);

    // Assert
    assertEquals(List.of(3L, 2L, 1L, 5L, 4L), executionOrder);
  }

  @Test
  void testCalculateExecutionOrder_SingleNode() {
    // Arrange
    Map<Long, Set<Long>> graph = new HashMap<>();
    graph.put(1L, Collections.emptySet());

    // Act
    List<Long> executionOrder = etlService.calculateExecutionOrder(graph);

    // Assert
    assertEquals(List.of(1L), executionOrder);
  }

  @Test
  void testCalculateExecutionOrder_DisconnectedGraph() {
    // Arrange
    Map<Long, Set<Long>> graph = new HashMap<>();
    graph.put(1L, Collections.emptySet());
    graph.put(2L, Collections.emptySet());
    graph.put(3L, Collections.emptySet());

    // Act
    List<Long> executionOrder = etlService.calculateExecutionOrder(graph);

    // Assert
    assertTrue(executionOrder.containsAll(List.of(1L, 2L, 3L)));
  }

  @Test
  void testCalculateExecutionOrder_CyclicDependency() {
    // Arrange
    Map<Long, Set<Long>> graph = new HashMap<>();
    graph.put(1L, Set.of(2L));
    graph.put(2L, Set.of(1L));

    // Act & Assert
    assertThrows(IllegalStateException.class, () -> etlService.calculateExecutionOrder(graph));
  }

  @Test
  void testCalculateExecutionOrder_EmptyGraph() {
    // Arrange
    Map<Long, Set<Long>> graph = new HashMap<>();

    // Act
    List<Long> executionOrder = etlService.calculateExecutionOrder(graph);

    // Assert
    assertTrue(executionOrder.isEmpty());
  }
}
