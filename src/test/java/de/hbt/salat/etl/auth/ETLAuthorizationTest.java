package de.hbt.salat.etl.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static de.hbt.salat.auth.domain.AccessLevel.READ;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.etl.domain.ETLDefinition;

/**
 * Das Recht an einer Definition, gefragt über ihre id (#1324): so nennen die Einträge der
 * Ausführungshistorie ihre Definition.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLAuthorizationTest {

  @Mock
  private AuthorizedUser authorizedUser;

  @Mock
  private AuthService authService;

  @InjectMocks
  private ETLAuthorization authorization;

  @Test
  void the_rule_is_asked_for_the_id_and_the_level() {
    lenient().when(authService.isAuthorized(eq("ETL"), any(), eq(READ), eq("9"))).thenReturn(true);

    assertThat(authorization.isAuthorizedForDefinitionId(9L, READ)).isTrue();
    assertThat(authorization.isAuthorizedForDefinitionId(11L, READ)).isFalse();
  }

  @Test
  void asked_with_the_definition_the_answer_is_the_same() {
    lenient().when(authService.isAuthorized(eq("ETL"), any(), eq(READ), eq("9"))).thenReturn(true);

    assertThat(authorization.isAuthorized(definition(9L), READ)).isTrue();
    assertThat(authorization.isAuthorized(definition(11L), READ)).isFalse();
  }

  @Test
  void the_management_reads_every_definition_without_a_rule() {
    when(authorizedUser.isManager()).thenReturn(true);

    assertThat(authorization.isAuthorizedForDefinitionId(11L, READ)).isTrue();
    verifyNoInteractions(authService);
  }

  @Test
  void an_administrator_reads_every_definition_without_a_rule() {
    when(authorizedUser.isAdmin()).thenReturn(true);

    assertThat(authorization.isAuthorizedForDefinitionId(11L, READ)).isTrue();
    verify(authorizedUser).isAdmin();
    verifyNoInteractions(authService);
  }

  private static ETLDefinition definition(long id) {
    var definition = new ETLDefinition();
    ReflectionTestUtils.setField(definition, "id", id);
    return definition;
  }

}
