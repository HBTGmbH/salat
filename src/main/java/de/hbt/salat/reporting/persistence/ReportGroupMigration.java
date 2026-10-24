package de.hbt.salat.reporting.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import de.hbt.salat.common.GlobalConstants;

/**
 * Moves the report definitions from the prefix in their name into report groups (#1523), by the rules of
 * {@link ReportGroupMigrationPlan}. Runs once, in the changeset that follows the creation of the table;
 * the unique key on group and title comes after it.
 *
 * <p>A people lead is read off the login that owns the report as it is at the time of the migration —
 * the status {@code pv}; a manager has a status of their own. The rows keep their audit fields: the
 * migration is no change anybody made.
 */
public class ReportGroupMigration implements CustomTaskChange {

  private static final String AUTHOR = "liquibase #1523";

  private String confirmation = "Keine Report-Definitionen zu überführen";

  @Override
  public void execute(Database database) throws CustomChangeException {
    var connection = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
    try {
      var plan = ReportGroupMigrationPlan.plan(readRows(connection));
      var now = new Timestamp(System.currentTimeMillis());
      var named = new HashMap<String, Long>();
      for (var name : plan.groupNames()) {
        named.put(name, insertGroup(connection, name, null, now));
      }
      var personal = new HashMap<Long, Long>();
      for (var ownerUserId : plan.personalOwners()) {
        personal.put(ownerUserId, insertGroup(connection, null, ownerUserId, now));
      }
      try (var update = connection.prepareStatement(
          "UPDATE report_definition SET name = ?, report_group_id = ? WHERE id = ?")) {
        for (var placement : plan.placements()) {
          var target = placement.target();
          update.setString(1, placement.title());
          var groupId = target.groupName() != null ? named.get(target.groupName())
              : target.ownerUserId() != null ? personal.get(target.ownerUserId()) : null;
          if (groupId == null) {
            update.setNull(2, Types.BIGINT);
          } else {
            update.setLong(2, groupId);
          }
          update.setLong(3, placement.id());
          update.addBatch();
        }
        update.executeBatch();
      }
      confirmation = summary(plan);
    } catch (SQLException e) {
      throw new CustomChangeException("Die Report-Definitionen ließen sich nicht in Gruppen überführen", e);
    }
  }

  private static ArrayList<ReportGroupMigrationPlan.Row> readRows(Connection connection) throws SQLException {
    var rows = new ArrayList<ReportGroupMigrationPlan.Row>();
    try (var statement = connection.createStatement();
         var result = statement.executeQuery("""
             SELECT r.id, r.name, r.owner_user_id, u.status, r.created
               FROM report_definition r
               LEFT JOIN salat_user u ON u.id = r.owner_user_id
             """)) {
      while (result.next()) {
        Long ownerUserId = result.getObject(3) == null ? null : result.getLong(3);
        var created = result.getTimestamp(5);
        rows.add(new ReportGroupMigrationPlan.Row(
            result.getLong(1),
            result.getString(2),
            ownerUserId,
            GlobalConstants.EMPLOYEE_STATUS_PV.equalsIgnoreCase(result.getString(4)),
            created != null ? created.toLocalDateTime() : null));
      }
    }
    return rows;
  }

  private static long insertGroup(Connection connection, String name, Long ownerUserId, Timestamp now)
      throws SQLException {
    try (var insert = connection.prepareStatement("""
        INSERT INTO report_group (name, owner_user_id, created, createdby, lastupdate, lastupdatedby, updatecounter)
        VALUES (?, ?, ?, ?, ?, ?, 0)
        """, Statement.RETURN_GENERATED_KEYS)) {
      insert.setString(1, name);
      if (ownerUserId == null) {
        insert.setNull(2, Types.BIGINT);
      } else {
        insert.setLong(2, ownerUserId);
      }
      insert.setTimestamp(3, now);
      insert.setString(4, AUTHOR);
      insert.setTimestamp(5, now);
      insert.setString(6, AUTHOR);
      insert.executeUpdate();
      try (var keys = insert.getGeneratedKeys()) {
        keys.next();
        return keys.getLong(1);
      }
    }
  }

  /** Numbers only: names of groups, reports and people stay out of the log. */
  private static String summary(ReportGroupMigrationPlan.Plan plan) {
    Map<ReportGroupMigrationPlan.Target, Long> counts = ReportGroupMigrationPlan.countByTarget(plan);
    long inNamed = counts.entrySet().stream().filter(e -> e.getKey().groupName() != null).mapToLong(Map.Entry::getValue).sum();
    long inPersonal = counts.entrySet().stream().filter(e -> e.getKey().ownerUserId() != null).mapToLong(Map.Entry::getValue).sum();
    long without = counts.entrySet().stream().filter(e -> e.getKey().isNone()).mapToLong(Map.Entry::getValue).sum();
    return "Report-Gruppen (#1523): " + plan.groupNames().size() + " Gruppen mit " + inNamed + " Reports, "
        + plan.personalOwners().size() + " persönliche Gruppen mit " + inPersonal + " Reports, "
        + without + " Reports ohne Gruppe";
  }

  @Override
  public String getConfirmationMessage() {
    return confirmation;
  }

  @Override
  public void setUp() {
    // nothing to prepare
  }

  @Override
  public void setFileOpener(ResourceAccessor resourceAccessor) {
    // reads no files
  }

  @Override
  public ValidationErrors validate(Database database) {
    return new ValidationErrors();
  }
}
