package de.hbt.salat.reporting.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static de.hbt.salat.auth.domain.AccessLevel.DELETE;
import static de.hbt.salat.auth.domain.AccessLevel.EXECUTE;
import static de.hbt.salat.auth.domain.AccessLevel.WRITE;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.reporting.domain.ReportDefinition;

/**
 * Who owns a report definition (#1330): the login it was created under, by the id of that login —
 * not by the login name in {@code createdby}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReportAuthorizationTest {

  private static final long OWNER = 5L;

  @Mock
  private AuthorizedUser authorizedUser;

  @Mock
  private AuthService authService;

  @InjectMocks
  private ReportAuthorization reportAuthorization;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isPeopleLead()).thenReturn(true);
  }

  @Test
  void a_people_lead_changes_and_deletes_what_they_own() {
    actingAs("pl", OWNER);

    assertThat(reportAuthorization.isAuthorized(report(OWNER, "pl"), WRITE)).isTrue();
    assertThat(reportAuthorization.isAuthorized(report(OWNER, "pl"), DELETE)).isTrue();
  }

  @Test
  void a_people_lead_executes_but_does_not_change_what_somebody_else_owns() {
    actingAs("pl", OWNER);

    assertThat(reportAuthorization.isAuthorized(report(9L, "other"), EXECUTE)).isTrue();
    assertThat(reportAuthorization.isAuthorized(report(9L, "other"), WRITE)).isFalse();
  }

  @Test
  void a_renamed_login_keeps_its_reports() {
    // createdby still carries the old name; the id is what counts
    actingAs("pl-renamed", OWNER);

    assertThat(reportAuthorization.isAuthorized(report(OWNER, "pl"), WRITE)).isTrue();
  }

  @Test
  void a_new_login_with_a_name_given_out_before_inherits_nothing() {
    actingAs("pl", 9L);

    assertThat(reportAuthorization.isAuthorized(report(OWNER, "pl"), WRITE)).isFalse();
  }

  @Test
  void a_report_without_an_owner_is_left_to_the_management() {
    actingAs("pl", OWNER);

    assertThat(reportAuthorization.isAuthorized(report(null, "pl"), WRITE)).isFalse();

    when(authorizedUser.isManager()).thenReturn(true);
    assertThat(reportAuthorization.isAuthorized(report(null, "pl"), WRITE)).isTrue();
  }

  @Test
  void the_owner_of_a_new_record_is_the_login_the_caller_acts_as() {
    actingAs("pl", OWNER);
    assertThat(reportAuthorization.ownerForNewRecord()).isEqualTo(OWNER);

    // two logins of the same name: none of them is handed the record
    when(authService.findUserIds("pl")).thenReturn(Set.of(OWNER, 9L));
    assertThat(reportAuthorization.ownerForNewRecord()).isNull();

    when(authService.findUserIds("pl")).thenReturn(Set.of());
    assertThat(reportAuthorization.ownerForNewRecord()).isNull();
  }

  private void actingAs(String loginname, long userId) {
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(loginname);
    when(authService.findUserIds(loginname)).thenReturn(Set.of(userId));
  }

  private static ReportDefinition report(Long ownerUserId, String createdby) {
    var report = new ReportDefinition();
    report.setOwnerUserId(ownerUserId);
    ReflectionTestUtils.setField(report, "createdby", createdby);
    return report;
  }
}
