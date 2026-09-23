package org.tb.etl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.common.LocalDateRange;
import org.tb.etl.auth.ETLAuthorization;
import org.tb.etl.domain.ETLDefinition;
import org.tb.etl.domain.ETLDefinition.ReferencePeriod;
import org.tb.etl.domain.ETLRunHistory;
import org.tb.etl.domain.ETLRunHistory.Status;
import org.tb.etl.domain.ETLRunHistory.Trigger;
import org.tb.etl.domain.SqlStatements;
import org.tb.etl.persistence.ETLDefinitionRepository;
import org.tb.etl.persistence.ETLExecutionHistoryRepository;
import org.tb.etl.persistence.ETLRunHistoryRepository;
import org.tb.etl.service.SchemaDiffService.Diff;

@ExtendWith(MockitoExtension.class)
public class ETLServiceTest {

  private static final LocalDateRange ONE_MONTH =
      new LocalDateRange(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 20));

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

  private record RunState(Status status, Trigger triggeredBy, LocalDateTime startedAt,
                          LocalDateTime finishedAt, String message) {}

  @BeforeEach
  void setUp() {
    lenient().when(runHistoryRepo.save(any())).thenAnswer(invocation -> {
      ETLRunHistory run = invocation.getArgument(0);
      savedRuns.add(new RunState(run.getStatus(), run.getTriggeredBy(), run.getStartedAt(),
          run.getFinishedAt(), run.getMessage()));
      return run;
    });
  }

  @Test
  void a_run_is_recorded_when_it_starts_and_again_when_it_ends() {
    givenDefinition("worked-hours");

    etlService.execute(ONE_MONTH, List.of("worked-hours"), true);

    assertThat(savedRuns).hasSize(2);
    assertThat(savedRuns.getFirst().status()).isEqualTo(Status.RUNNING);
    assertThat(savedRuns.getFirst().startedAt()).isNotNull();
    assertThat(savedRuns.getFirst().finishedAt()).isNull();
    assertThat(savedRuns.getLast().status()).isEqualTo(Status.SUCCEEDED);
    assertThat(savedRuns.getLast().finishedAt()).isNotNull();
    assertThat(savedRuns.getLast().message()).contains("1 Definition(en) ausgeführt");
  }

  @Test
  void a_scheduled_run_is_distinguishable_from_a_manual_one() {
    givenDefinition("worked-hours");
    when(authorization.isAuthorized(any(), any())).thenReturn(true);

    etlService.execute(ONE_MONTH, List.of("worked-hours"), false);

    assertThat(savedRuns).extracting(RunState::triggeredBy).containsOnly(Trigger.MANUAL);
  }

  @Test
  void a_failing_definition_marks_the_run_as_failed_and_names_it() {
    givenDefinition("worked-hours");
    when(jdbcTemplate.update(anyString())).thenThrow(new DataAccessResourceFailureException("no connection"));

    etlService.execute(ONE_MONTH, List.of("worked-hours"), true);

    assertThat(savedRuns.getLast().status()).isEqualTo(Status.FAILED);
    assertThat(savedRuns.getLast().message()).contains("worked-hours");
    assertThat(savedRuns.getLast().finishedAt()).isNotNull();
  }

  @Test
  void an_unexpected_exception_aborts_the_run_and_lands_in_its_entry() {
    givenDefinition("worked-hours");
    when(parameterResolver.resolve(anyString(), any())).thenThrow(new IllegalStateException("unknown parameter"));

    assertThatThrownBy(() -> etlService.execute(ONE_MONTH, List.of("worked-hours"), true))
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

    assertThatThrownBy(() -> etlService.executeAll(ONE_MONTH, true))
        .isInstanceOf(IllegalStateException.class);

    assertThat(savedRuns).hasSize(2);
    assertThat(savedRuns.getLast().status()).isEqualTo(Status.FAILED);
    assertThat(savedRuns.getLast().message()).contains("Lauf abgebrochen nach 0 Definition(en)");
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
    lenient().when(parameterResolver.resolve(anyString(), any())).thenAnswer(i -> i.getArgument(0));
    lenient().when(schemaDiffService.diffAround(any(), anyString())).thenAnswer(invocation -> {
      invocation.getArgument(0, Runnable.class).run();
      return new Diff(List.of(), List.of(), List.of());
    });
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