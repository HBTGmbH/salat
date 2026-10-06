package de.hbt.salat.reporting.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.event.SignsRenamedEvent;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.reporting.auth.ReportAuthorization;
import de.hbt.salat.reporting.domain.ReportDefinition;
import de.hbt.salat.reporting.persistence.ReportDefinitionRepository;
import de.hbt.salat.reporting.persistence.OwnerReferences;
import de.hbt.salat.reporting.service.ReportService;

/** A renamed order or suborder names the report definitions that still carry the old sign (#1206). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReportSignReferenceListenerTest {

  private final ReportDefinitionRepository repository = mock(ReportDefinitionRepository.class);
  private final ReportSignReferenceListener listener = new ReportSignReferenceListener(
      new ReportService(repository, null, mock(ReportAuthorization.class), mock(AuthorizedUser.class),
          mock(OwnerReferences.class)));

  @Test
  void the_definitions_naming_the_old_sign_are_named_once() {
    when(repository.findAll()).thenReturn(List.of(
        definition("Umsatz", "select * from x where sign like 'CO/01/%'"),
        definition("Auslastung", "select * from x where sign = 'CO/01'"),
        definition("Nachbar", "select * from x where sign = 'CO/010'")));
    var event = new SignsRenamedEvent("CO/01", "CO/X1", 1L, 1L);

    listener.onSignsRenamed(event);

    assertThat(event.getNotices()).singleElement().satisfies(notice -> {
      assertThat(notice.getErrorCode()).isEqualTo(ErrorCode.RP_DEFINITIONS_NAME_OLD_SIGN);
      assertThat(notice.getArguments()).containsExactly("Auslastung, Umsatz", "CO/01");
    });
  }

  @Test
  void without_such_a_definition_there_is_nothing_to_say() {
    when(repository.findAll()).thenReturn(List.of(definition("Nachbar", "select 'CO/010'")));
    var event = new SignsRenamedEvent("CO/01", "CO/X1", 1L, 1L);

    listener.onSignsRenamed(event);

    assertThat(event.getNotices()).isEmpty();
  }

  private static ReportDefinition definition(String name, String sql) {
    var definition = new ReportDefinition();
    definition.setName(name);
    definition.setSql(sql);
    return definition;
  }
}
