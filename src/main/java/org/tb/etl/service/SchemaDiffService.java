package org.tb.etl.service;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@AllArgsConstructor
public class SchemaDiffService {

  private final JdbcTemplate jdbcTemplate;

  // Public API

  public Snapshot snapshot(String schema) {
    Map<Key, Obj> objects = new LinkedHashMap<>();
    objects.putAll(listTables(schema));
    objects.putAll(listViews(schema));
    objects.putAll(listRoutines(schema, "PROCEDURE"));
    objects.putAll(listRoutines(schema, "FUNCTION"));
    objects.putAll(listTriggers(schema));
    objects.putAll(listEvents(schema));
    return new Snapshot(schema, objects);
  }

  public Diff diff(Snapshot before, Snapshot after) {
    Set<Key> beforeKeys = before.objects.keySet();
    Set<Key> afterKeys = after.objects.keySet();

    List<Obj> created = new ArrayList<>();
    List<Obj> dropped = new ArrayList<>();
    List<Modified> modified = new ArrayList<>();

    for (Key k : afterKeys) {
      if (!beforeKeys.contains(k)) {
        created.add(after.objects.get(k));
      }
    }
    for (Key k : beforeKeys) {
      if (!afterKeys.contains(k)) {
        dropped.add(before.objects.get(k));
      }
    }
    for (Key k : intersection(beforeKeys, afterKeys)) {
      Obj b = before.objects.get(k);
      Obj a = after.objects.get(k);
      if (!Objects.equals(b.definitionHash, a.definitionHash)) {
        modified.add(new Modified(b, a));
      }
    }
    return new Diff(created, dropped, modified);
  }

  public Diff diffAround(Runnable ddlBlock, String schema) {
    Snapshot before = snapshot(schema);
    ddlBlock.run(); // Achtung: MySQL DDL committet implizit
    Snapshot after = snapshot(schema);
    return diff(before, after);
  }

  // Snapshot-Helfer

  private Map<Key, Obj> listTables(String schema) {
    String sql = """
        SELECT TABLE_NAME
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
        """;
    List<String> names = jdbcTemplate.queryForList(sql, String.class, schema);
    Map<Key, Obj> out = new LinkedHashMap<>();
    for (String name : names) {
      String ddl = showCreateTable(schema, name);
      out.put(new Key(schema, name, "TABLE"), new Obj(schema, name, "TABLE", hash(ddl), ddl));
    }
    return out;
  }

  private Map<Key, Obj> listViews(String schema) {
    String sql = """
        SELECT TABLE_NAME
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'VIEW'
        """;
    List<String> names = jdbcTemplate.queryForList(sql, String.class, schema);
    Map<Key, Obj> out = new LinkedHashMap<>();
    for (String name : names) {
      String ddl = showCreateView(schema, name);
      out.put(new Key(schema, name, "VIEW"), new Obj(schema, name, "VIEW", hash(ddl), ddl));
    }
    return out;
  }

  private Map<Key, Obj> listRoutines(String schema, String routineType) {
    String sql = """
        SELECT ROUTINE_NAME
        FROM information_schema.ROUTINES
        WHERE ROUTINE_SCHEMA = ? AND ROUTINE_TYPE = ?
        """;
    List<String> names = jdbcTemplate.queryForList(sql, String.class, schema, routineType);
    Map<Key, Obj> out = new LinkedHashMap<>();
    for (String name : names) {
      String ddl = showCreateRoutine(routineType, schema, name);
      out.put(new Key(schema, name, routineType), new Obj(schema, name, routineType, hash(ddl), ddl));
    }
    return out;
  }

  private Map<Key, Obj> listTriggers(String schema) {
    String sql = """
        SELECT TRIGGER_NAME
        FROM information_schema.TRIGGERS
        WHERE TRIGGER_SCHEMA = ?
        """;
    List<String> names = jdbcTemplate.queryForList(sql, String.class, schema);
    Map<Key, Obj> out = new LinkedHashMap<>();
    for (String name : names) {
      String ddl = showCreateTrigger(schema, name);
      out.put(new Key(schema, name, "TRIGGER"), new Obj(schema, name, "TRIGGER", hash(ddl), ddl));
    }
    return out;
  }

  private Map<Key, Obj> listEvents(String schema) {
    // Event Scheduler muss aktiviert sein, sonst ist die Tabelle u.U. leer
    String sql = """
        SELECT EVENT_NAME
        FROM information_schema.EVENTS
        WHERE EVENT_SCHEMA = ?
        """;
    List<String> names = jdbcTemplate.queryForList(sql, String.class, schema);
    Map<Key, Obj> out = new LinkedHashMap<>();
    for (String name : names) {
      String ddl = showCreateEvent(schema, name);
      out.put(new Key(schema, name, "EVENT"), new Obj(schema, name, "EVENT", hash(ddl), ddl));
    }
    return out;
  }

  // SHOW CREATE Reader

  private String showCreateTable(String schema, String table) {
    String sql = "SHOW CREATE TABLE `" + schema + "`.`" + table + "`";
    return jdbcTemplate.query(sql, rs -> {
      if (rs.next()) {
        return rs.getString("Create Table"); // MySQL Spaltenname
      }
      return "";
    });
  }

  private String showCreateView(String schema, String view) {
    String sql = "SHOW CREATE VIEW `" + schema + "`.`" + view + "`";
    return jdbcTemplate.query(sql, rs -> {
      if (rs.next()) {
        return rs.getString("Create View");
      }
      return "";
    });
  }

  private String showCreateRoutine(String type, String schema, String name) {
    String sql = "SHOW CREATE " + type + " `" + schema + "`.`" + name + "`";
    return jdbcTemplate.query(sql, rs -> {
      if (rs.next()) {
        // Spaltennamen unterscheiden sich nach Typ
        if ("PROCEDURE".equalsIgnoreCase(type)) return rs.getString("Create Procedure");
        else return rs.getString("Create Function");
      }
      return "";
    });
  }

  private String showCreateTrigger(String schema, String name) {
    String sql = "SHOW CREATE TRIGGER `" + schema + "`.`" + name + "`";
    return jdbcTemplate.query(sql, rs -> rs.next() ? rs.getString("SQL Original Statement") : "");
  }

  private String showCreateEvent(String schema, String name) {
    String sql = "SHOW CREATE EVENT `" + schema + "`.`" + name + "`";
    return jdbcTemplate.query(sql, rs -> rs.next() ? rs.getString("Create Event") : "");
  }

  // Utils

  private static <T> Set<T> intersection(Set<T> a, Set<T> b) {
    Set<T> r = new LinkedHashSet<>(a);
    r.retainAll(b);
    return r;
  }

  private static String hash(String ddl) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] h = md.digest(ddl == null ? new byte[0] : ddl.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(64);
      for (byte b : h) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // Records

  public record Snapshot(String schema, Map<Key, Obj> objects) {}

  public record Diff(List<Obj> created, List<Obj> dropped, List<Modified> modified) {}

  public record Modified(Obj before, Obj after) {}

  public record Key(String schema, String name, String type) {}

  public record Obj(String schema, String name, String type, String definitionHash, String ddl) {}
}
