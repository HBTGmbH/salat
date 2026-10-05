package de.hbt.salat.etl.service;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Vergleicht, welche Objekte eines Schemas vor und nach einem Block von Anweisungen bestehen.
 *
 * <p><b>Nur Typ und Name, in einer Abfrage</b> (#1356): {@code ETLService} legt den Vergleich je
 * Referenzperiode zweimal um eine Definition, und gebraucht wird nur, was angelegt und was entfernt
 * wurde. Früher holte jeder Snapshot zusätzlich je Objekt dessen {@code SHOW CREATE} — bei 85
 * Tabellen und Views über 90 Roundtrips, und bei den Tages-Definitionen kostete der Rahmen mehr als
 * ihr SQL. Wer eine geänderte Definition erkennen will, braucht dafür einen eigenen Weg.
 */
@Service
@AllArgsConstructor
public class SchemaDiffService {

  private static final String SCHEMA_OBJECTS = """
      SELECT CASE TABLE_TYPE WHEN 'VIEW' THEN 'VIEW' ELSE 'TABLE' END AS object_type, TABLE_NAME AS object_name
      FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = ? AND TABLE_TYPE IN ('BASE TABLE', 'VIEW')
      UNION ALL
      SELECT ROUTINE_TYPE, ROUTINE_NAME
      FROM information_schema.ROUTINES
      WHERE ROUTINE_SCHEMA = ? AND ROUTINE_TYPE IN ('PROCEDURE', 'FUNCTION')
      UNION ALL
      SELECT 'TRIGGER', TRIGGER_NAME
      FROM information_schema.TRIGGERS
      WHERE TRIGGER_SCHEMA = ?
      UNION ALL
      SELECT 'EVENT', EVENT_NAME
      FROM information_schema.EVENTS
      WHERE EVENT_SCHEMA = ?
      """;

  private final JdbcTemplate jdbcTemplate;

  // Public API

  public Snapshot snapshot(String schema) {
    List<SchemaObject> objects = jdbcTemplate.query(SCHEMA_OBJECTS,
        (rs, rowNum) -> new SchemaObject(rs.getString("object_type"), rs.getString("object_name")),
        schema, schema, schema, schema);
    return new Snapshot(schema, new LinkedHashSet<>(objects));
  }

  public Diff diff(Snapshot before, Snapshot after) {
    List<SchemaObject> created = new ArrayList<>(after.objects);
    created.removeAll(before.objects);
    List<SchemaObject> dropped = new ArrayList<>(before.objects);
    dropped.removeAll(after.objects);
    return new Diff(created, dropped);
  }

  public Diff diffAround(Runnable ddlBlock, String schema) {
    Snapshot before = snapshot(schema);
    ddlBlock.run(); // Achtung: MySQL DDL committet implizit
    Snapshot after = snapshot(schema);
    return diff(before, after);
  }

  // Records

  public record Snapshot(String schema, Set<SchemaObject> objects) {}

  public record Diff(List<SchemaObject> created, List<SchemaObject> dropped) {}

  public record SchemaObject(String type, String name) {}
}
