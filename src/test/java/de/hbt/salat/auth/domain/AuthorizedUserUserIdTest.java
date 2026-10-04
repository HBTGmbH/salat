package de.hbt.salat.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import de.hbt.salat.common.web.UiState;

/**
 * The id of the login the caller acts as (#1330) — what a record stores to name its owner. It comes
 * with the authentication and with a switch of login, never from a lookup by name.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class AuthorizedUserUserIdTest {

  private final UiState uiState = mock(UiState.class);
  private final AuthorizedUser authorizedUser = new AuthorizedUser(uiState);

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void the_id_is_the_one_the_authentication_carries() {
    signedInAs("pl", 5L);

    assertThat(authorizedUser.getEffectiveUserId()).isEqualTo(5L);
  }

  @Test
  void while_impersonating_it_is_the_id_recorded_with_the_switch() {
    signedInAs("pl", 5L);
    when(uiState.getValue(AuthUiStateKeyContributor.IMPERSONATE_LOGIN_SIGN)).thenReturn("ma");
    when(uiState.getLongValue(AuthUiStateKeyContributor.IMPERSONATE_LOGIN_ID)).thenReturn(7L);

    assertThat(authorizedUser.getEffectiveUserId()).isEqualTo(7L);
  }

  @Test
  void an_authentication_without_a_login_id_has_none() {
    SecurityContextHolder.getContext().setAuthentication(
        new TestingAuthenticationToken("pl", null, EmployeeStatusAuthorities.from("pv").stream().toList()));

    assertThat(authorizedUser.getEffectiveUserId()).isNull();
  }

  @Test
  void a_job_has_no_login_and_so_no_id() {
    signedInAs("pl", 5L);
    authorizedUser.initForJob();

    assertThat(authorizedUser.getEffectiveUserId()).isNull();
  }

  @Test
  void the_login_id_is_no_role() {
    assertThat(EmployeeStatusAuthorities.from("ma", 5L))
        .extracting(authority -> authority.getAuthority())
        .contains("LOGIN_ID_5")
        .allSatisfy(authority -> assertThat(authority).isNotEqualTo("ROLE_LOGIN_ID_5"));
  }

  private static void signedInAs(String loginname, long userId) {
    SecurityContextHolder.getContext().setAuthentication(
        new TestingAuthenticationToken(loginname, null, EmployeeStatusAuthorities.from("pv", userId).stream().toList()));
  }
}
