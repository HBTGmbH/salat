package org.tb.etl.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.tb.etl.auth.ETLAuthorization;
import org.tb.etl.persistence.ETLDefinitionRepository;
import org.tb.etl.persistence.ETLExecutionHistoryRepository;

@ExtendWith(MockitoExtension.class)
public class ETLServiceTest {

  @Mock
  private ETLDefinitionRepository definitionRepo;

  @Mock
  private ETLExecutionHistoryRepository historyRepo;

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