package de.hbt.salat.etl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import de.hbt.salat.etl.service.SchemaDiffService.SchemaObject;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class SchemaDiffServiceTest {

  private static final SchemaObject WORKED_HOURS = new SchemaObject("TABLE", "stat_worked_hours");
  private static final SchemaObject TMP_HOURS = new SchemaObject("TABLE", "tmp_hours");
  private static final SchemaObject HOURS_VIEW = new SchemaObject("VIEW", "v_hours");
  private static final SchemaObject RECALCULATE = new SchemaObject("PROCEDURE", "recalculate");

  @Mock
  private JdbcTemplate jdbcTemplate;

  @InjectMocks
  private SchemaDiffService schemaDiffService;

  /** Die Zahl der Roundtrips je Snapshot hängt nicht an der Zahl der Objekte (#1356). */
  @Test
  void a_snapshot_sends_a_single_query_however_many_objects_the_schema_holds() {
    var manyTables = IntStream.range(0, 200).mapToObj(i -> new SchemaObject("TABLE", "table_" + i)).toList();
    givenSchemaHolds(manyTables);

    var snapshot = schemaDiffService.snapshot("salat");

    assertThat(snapshot.objects()).hasSize(200);
    verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class), any(Object[].class));
    verifyNoMoreInteractions(jdbcTemplate);
  }

  @Test
  void an_object_created_by_the_block_is_listed_as_created() {
    givenSchemaHolds(List.of(WORKED_HOURS, HOURS_VIEW), List.of(WORKED_HOURS, TMP_HOURS, HOURS_VIEW));

    var diff = schemaDiffService.diffAround(() -> {}, "salat");

    assertThat(diff.created()).containsExactly(TMP_HOURS);
    assertThat(diff.dropped()).isEmpty();
  }

  @Test
  void an_object_dropped_by_the_block_is_listed_as_dropped() {
    givenSchemaHolds(List.of(WORKED_HOURS, TMP_HOURS, RECALCULATE), List.of(WORKED_HOURS, RECALCULATE));

    var diff = schemaDiffService.diffAround(() -> {}, "salat");

    assertThat(diff.created()).isEmpty();
    assertThat(diff.dropped()).containsExactly(TMP_HOURS);
  }

  /** Eine Tabelle und eine View desselben Namens sind zwei Objekte, wie früher über Typ und Name. */
  @Test
  void a_view_replacing_a_table_of_the_same_name_is_one_created_and_one_dropped() {
    var tableHours = new SchemaObject("TABLE", "hours");
    var viewHours = new SchemaObject("VIEW", "hours");
    givenSchemaHolds(List.of(tableHours), List.of(viewHours));

    var diff = schemaDiffService.diffAround(() -> {}, "salat");

    assertThat(diff.created()).containsExactly(viewHours);
    assertThat(diff.dropped()).containsExactly(tableHours);
  }

  /**
   * Beantwortet jede Abfrage mit dem nächsten Zustand des Schemas — und reicht die Zeilen durch den
   * {@link RowMapper} des Service, damit auch dessen Spaltennamen geprüft sind.
   */
  @SafeVarargs
  @SuppressWarnings("unchecked")
  private void givenSchemaHolds(List<SchemaObject>... states) {
    Deque<List<SchemaObject>> remaining = new ArrayDeque<>(List.of(states));
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
      RowMapper<SchemaObject> mapper = invocation.getArgument(1);
      var rows = new ArrayList<SchemaObject>();
      for (SchemaObject object : remaining.removeFirst()) {
        rows.add(mapper.mapRow(row(object), rows.size()));
      }
      return rows;
    });
  }

  private static ResultSet row(SchemaObject object) throws SQLException {
    var rs = mock(ResultSet.class);
    when(rs.getString("object_type")).thenReturn(object.type());
    when(rs.getString("object_name")).thenReturn(object.name());
    return rs;
  }
}
