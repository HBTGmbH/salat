package de.hbt.salat.etl.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The name of an ETL definition becomes unique and required (#1207). Before the constraint is added,
 * the migration stops on a table that does not allow it — naming the definitions to correct rather
 * than failing on a key.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLDefinitionNamesCheckTest {

  private Connection connection;
  private Database database;

  @BeforeEach
  void setUp() throws SQLException {
    connection = DriverManager.getConnection("jdbc:h2:mem:etl-names-check;MODE=MySQL;DB_CLOSE_DELAY=-1");
    try (var statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS etl_definition");
      statement.execute("CREATE TABLE etl_definition (id BIGINT PRIMARY KEY, name VARCHAR(255))");
    }
    database = mock(Database.class);
    when(database.getConnection()).thenReturn(new JdbcConnection(connection));
  }

  @AfterEach
  void tearDown() throws SQLException {
    connection.close();
  }

  @Test
  void passes_when_every_definition_has_a_name_of_its_own() throws SQLException {
    insert(1, "stunden");
    insert(2, "umsatz");

    assertThatCode(() -> new ETLDefinitionNamesCheck().execute(database)).doesNotThrowAnyException();
  }

  @Test
  void stops_and_names_the_definitions_that_share_a_name() throws SQLException {
    insert(1, "stunden");
    insert(2, "umsatz");
    insert(3, "stunden");

    assertThatThrownBy(() -> new ETLDefinitionNamesCheck().execute(database))
        .isInstanceOf(CustomChangeException.class)
        .hasMessageContaining("\"stunden\" tragen die ids 1, 3")
        .hasMessageNotContaining("umsatz");
  }

  @Test
  void stops_and_names_a_definition_without_a_name() throws SQLException {
    insert(1, "stunden");
    insert(4, null);
    insert(5, " ");

    assertThatThrownBy(() -> new ETLDefinitionNamesCheck().execute(database))
        .isInstanceOf(CustomChangeException.class)
        .hasMessageContaining("id 4 hat keinen Namen")
        .hasMessageContaining("id 5 hat keinen Namen");
  }

  private void insert(long id, String name) throws SQLException {
    try (var statement = connection.prepareStatement("INSERT INTO etl_definition (id, name) VALUES (?, ?)")) {
      statement.setLong(1, id);
      statement.setString(2, name);
      statement.executeUpdate();
    }
  }

}
