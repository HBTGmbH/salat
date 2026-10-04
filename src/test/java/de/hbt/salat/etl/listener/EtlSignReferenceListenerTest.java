package de.hbt.salat.etl.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.common.event.SignsRenamedEvent;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.etl.domain.ETLDefinition;
import de.hbt.salat.etl.domain.SqlStatements;
import de.hbt.salat.etl.persistence.ETLDefinitionRepository;
import de.hbt.salat.etl.service.ETLService;

/** Ein umbenannter Auftrag nennt die ETL-Definitionen, die das alte Kürzel noch enthalten (#1206). */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class EtlSignReferenceListenerTest {

  @Mock
  private ETLDefinitionRepository definitionRepo;

  @InjectMocks
  private ETLService etlService;

  @Test
  void eine_definition_mit_dem_alten_kuerzel_in_einem_ihrer_schritte_wird_genannt() {
    when(definitionRepo.findAll()).thenReturn(List.of(
        definition("stat_umsatz", null, "insert into s select * from t where co = 'CO'", null),
        definition("stat_andere", "delete from s", "insert into s select * from t where co = 'COX'", null)));
    var event = new SignsRenamedEvent("CO", "NEW", 1L, 1L);

    new EtlSignReferenceListener(etlService).onSignsRenamed(event);

    assertThat(event.getNotices()).singleElement().satisfies(notice -> {
      assertThat(notice.getErrorCode()).isEqualTo(ErrorCode.ETL_DEFINITIONS_NAME_OLD_SIGN);
      assertThat(notice.getArguments()).containsExactly("stat_umsatz", "CO");
    });
  }

  private static ETLDefinition definition(String name, String init, String execute, String cleanup) {
    var definition = new ETLDefinition();
    definition.setName(name);
    definition.setInit(statements(init));
    definition.setExecute(statements(execute));
    definition.setCleanup(statements(cleanup));
    return definition;
  }

  private static SqlStatements statements(String sql) {
    return sql == null ? null : new SqlStatements(List.of(sql));
  }
}
