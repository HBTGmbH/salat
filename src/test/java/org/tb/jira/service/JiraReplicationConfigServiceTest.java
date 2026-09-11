package org.tb.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.InvalidDataException;
import org.tb.jira.domain.JiraApiFlavor;
import org.tb.jira.domain.JiraReplicationConfig;
import org.tb.jira.domain.JiraReplicationConfigData;
import org.tb.jira.persistence.JiraReplicationConfigRepository;

/**
 * Maintaining the replication configs from the user interface (#984).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraReplicationConfigServiceTest {

  private static final long ID = 42L;
  private static final String STORED_PASSWORD = "stored-token";

  @InjectMocks
  private JiraReplicationConfigService classUnderTest;

  @Mock
  private JiraReplicationConfigRepository configRepository;

  @Mock
  private JiraReplicationService jiraReplicationService;

  @Mock
  private AuthorizedUser authorizedUser;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isManager()).thenReturn(true);
    when(configRepository.save(any())).thenAnswer(invocation -> {
      JiraReplicationConfig config = invocation.getArgument(0);
      if (config.getId() == null) {
        setId(config, ID);
      }
      return config;
    });
  }

  @Test
  void an_empty_password_keeps_the_stored_one() {
    var stored = existingConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, data(null));

    assertThat(saved().getPassword()).isEqualTo(STORED_PASSWORD);
  }

  @Test
  void a_filled_password_replaces_the_stored_one() {
    when(configRepository.findById(ID)).thenReturn(Optional.of(existingConfig()));

    classUnderTest.update(ID, data("  new-token  "));

    assertThat(saved().getPassword()).isEqualTo("new-token");
  }

  @Test
  void a_new_replication_needs_a_password() {
    // On an edit an empty field means "keep"; there is nothing to keep on a new record, and the
    // replication would fail on its first run.
    assertThatThrownBy(() -> classUnderTest.create(data("   ")))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_PASSWORD_REQUIRED);

    verify(configRepository, never()).save(any());
  }

  @Test
  void what_the_user_interface_gets_to_see_carries_no_password() {
    var stored = existingConfig();
    when(configRepository.findAllByOrderByNameAsc()).thenReturn(List.of(stored));
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    // The absence is structural: the record has no password component at all, so neither a template
    // nor a log line can reach the stored token.
    assertThat(classUnderTest.getAll()).singleElement()
        .satisfies(info -> assertThat(info.username()).isEqualTo("jira-user"));
    assertThat(classUnderTest.getById(ID).getClass().getRecordComponents())
        .noneMatch(component -> component.getName().toLowerCase().contains("password"));
  }

  @Test
  void the_watermark_is_reset_rather_than_written() {
    var stored = existingConfig();
    stored.setLastMaxUpdated(LocalDateTime.of(2026, 3, 1, 2, 0));
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.resetWatermark(ID);

    assertThat(saved().getLastMaxUpdated()).isNull();
  }

  @Test
  void an_edit_leaves_the_watermark_alone() {
    var stored = existingConfig();
    var watermark = LocalDateTime.of(2026, 3, 1, 2, 0);
    stored.setLastMaxUpdated(watermark);
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, data("new-token"));

    assertThat(saved().getLastMaxUpdated()).isEqualTo(watermark);
  }

  @Test
  void switching_a_replication_off_and_on_writes_only_that_flag() {
    var stored = existingConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.setEnabled(ID, false);
    assertThat(saved().getEnabled()).isFalse();

    classUnderTest.setEnabled(ID, true);
    assertThat(saved().getEnabled()).isTrue();
    assertThat(saved().getPassword()).isEqualTo(STORED_PASSWORD);
  }

  @Test
  void a_failed_run_is_reported_back_without_the_password_in_it() {
    var stored = existingConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));
    doThrow(new IllegalStateException("401 for user jira-user with token " + STORED_PASSWORD))
        .when(jiraReplicationService).runReplication(stored);

    var outcome = classUnderTest.runNow(ID);

    assertThat(outcome.success()).isFalse();
    assertThat(outcome.name()).isEqualTo("Alpha");
    assertThat(outcome.message()).doesNotContain(STORED_PASSWORD).contains("***");
  }

  @Test
  void a_run_started_by_hand_does_not_ask_whether_the_replication_is_enabled() {
    // Trying a config out before switching it on is the point of the button.
    var stored = existingConfig();
    stored.setEnabled(false);
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    assertThat(classUnderTest.runNow(ID).success()).isTrue();

    verify(jiraReplicationService).runReplication(stored);
  }

  @Test
  void a_replication_without_a_jql_query_could_only_ever_fail() {
    var withoutJql = new JiraReplicationConfigData("Alpha", "ALPHA", "https://jira.example.com",
        JiraApiFlavor.SERVER, "jira-user", "token", "  ", null, null, true);

    assertThatThrownBy(() -> classUnderTest.create(withoutJql))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_JQL_REQUIRED);
  }

  @Test
  void a_base_url_without_a_scheme_is_rejected() {
    var badUrl = new JiraReplicationConfigData("Alpha", "ALPHA", "jira.example.com",
        JiraApiFlavor.SERVER, "jira-user", "token", "project = ALPHA", null, null, true);

    assertThatThrownBy(() -> classUnderTest.create(badUrl))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_BASE_URL_INVALID);
  }

  @Test
  void a_page_size_of_zero_or_less_is_rejected() {
    var zeroPageSize = new JiraReplicationConfigData("Alpha", "ALPHA", "https://jira.example.com",
        JiraApiFlavor.SERVER, "jira-user", "token", "project = ALPHA", null, 0, true);

    assertThatThrownBy(() -> classUnderTest.create(zeroPageSize))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_PAGE_SIZE_INVALID);
  }

  @Test
  void a_missing_flavor_is_stored_as_server() {
    // What a row without an explicit flavor has always meant.
    classUnderTest.create(new JiraReplicationConfigData("Alpha", "ALPHA", "https://jira.example.com",
        null, "jira-user", "token", "project = ALPHA", null, null, true));

    assertThat(saved().getApiFlavor()).isEqualTo(JiraApiFlavor.SERVER);
  }

  @Test
  void deleting_a_replication_leaves_the_replicated_tickets_alone() {
    // jira_ticket hangs off customerorder_sign, not off the config — the rows are not wrong, only
    // no longer kept up to date.
    var stored = existingConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.delete(ID);

    verify(configRepository).delete(stored);
    verifyNoInteractions(jiraReplicationService);
  }

  @Test
  void an_unknown_replication_is_reported_as_such() {
    when(configRepository.findById(ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> classUnderTest.getById(ID))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_NOT_FOUND);
  }

  @Test
  void everything_here_needs_the_management() {
    when(authorizedUser.isManager()).thenReturn(false);

    assertThatThrownBy(() -> classUnderTest.getAll()).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.getById(ID)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.create(data("token"))).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.update(ID, data("token"))).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.delete(ID)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.setEnabled(ID, true)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.resetWatermark(ID)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.runNow(ID)).isInstanceOf(AuthorizationException.class);

    verifyNoInteractions(configRepository, jiraReplicationService);
  }

  private JiraReplicationConfig existingConfig() {
    var config = new JiraReplicationConfig();
    config.setName("Alpha");
    config.setCustomerorderSign("ALPHA");
    config.setBaseUrl("https://jira.example.com");
    config.setApiFlavor(JiraApiFlavor.SERVER);
    config.setUsername("jira-user");
    config.setPassword(STORED_PASSWORD);
    config.setJql("project = ALPHA");
    config.setEnabled(true);
    return config;
  }

  private static JiraReplicationConfigData data(String password) {
    return new JiraReplicationConfigData("Alpha", "ALPHA", "https://jira.example.com",
        JiraApiFlavor.SERVER, "jira-user", password, "project = ALPHA", null, 100, true);
  }

  private JiraReplicationConfig saved() {
    var captor = ArgumentCaptor.forClass(JiraReplicationConfig.class);
    verify(configRepository, atLeastOnce()).save(captor.capture());
    return captor.getValue();
  }

  private static ErrorCode firstCode(ErrorCodeException ex) {
    return ex.getMessages().get(0).getErrorCode();
  }

  /** The id is generated, so there is no setter; a stored record always has one. */
  private static void setId(JiraReplicationConfig config, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(config, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
  }
}
