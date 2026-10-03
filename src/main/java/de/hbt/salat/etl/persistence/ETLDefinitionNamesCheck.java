package de.hbt.salat.etl.persistence;

import java.sql.SQLException;
import java.util.ArrayList;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/**
 * Checks, before {@code etl_definition.name} becomes {@code NOT NULL} and unique (#1207), that the
 * definitions allow it — and names the ones that do not.
 *
 * <p>The definitions are maintained by hand in the database, so nothing in the application has ever
 * kept their names unique or filled. A constraint added over such a table would fail with a message
 * about a key, not about which definitions to fix; a precondition can only say that something is
 * wrong. This check stops the migration with the ids and names to correct, and changes nothing, so
 * the next start picks up where it stopped once they are corrected.
 */
public class ETLDefinitionNamesCheck implements CustomTaskChange {

  @Override
  public void execute(Database database) throws CustomChangeException {
    var connection = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
    var problems = new ArrayList<String>();
    try (var statement = connection.createStatement();
         var nameless = statement.executeQuery("SELECT id FROM etl_definition WHERE name IS NULL OR TRIM(name) = ''")) {
      while (nameless.next()) {
        problems.add("id " + nameless.getLong(1) + " hat keinen Namen");
      }
    } catch (SQLException e) {
      throw new CustomChangeException("Die Namen der ETL-Definitionen ließen sich nicht prüfen", e);
    }
    try (var statement = connection.createStatement();
         var duplicates = statement.executeQuery("""
             SELECT name, GROUP_CONCAT(id ORDER BY id SEPARATOR ', ')
               FROM etl_definition
              WHERE name IS NOT NULL
              GROUP BY name
             HAVING COUNT(*) > 1
             """)) {
      while (duplicates.next()) {
        problems.add("\"" + duplicates.getString(1) + "\" tragen die ids " + duplicates.getString(2));
      }
    } catch (SQLException e) {
      throw new CustomChangeException("Die Namen der ETL-Definitionen ließen sich nicht prüfen", e);
    }
    if (!problems.isEmpty()) {
      throw new CustomChangeException("Der Name einer ETL-Definition wird eindeutig und Pflicht (#1207). "
          + "Vorher sind diese Definitionen in der Datenbank zu korrigieren: " + String.join("; ", problems));
    }
  }

  @Override
  public String getConfirmationMessage() {
    return "Die Namen der ETL-Definitionen sind vorhanden und eindeutig";
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
