package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static de.hbt.salat.jira.OrderTree.customerorderWithId;
import static de.hbt.salat.jira.OrderTree.suborderWithId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.jira.domain.JiraApiFlavor;
import de.hbt.salat.jira.domain.JiraAuthMethod;
import de.hbt.salat.jira.domain.JiraFieldOption;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraReplicationConfigData;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.jira.persistence.JiraTicketRepository;
import de.hbt.salat.jira.persistence.OrderReferences;
import de.hbt.salat.order.domain.SuborderLocation;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.secret.domain.OAuthConnection;
import de.hbt.salat.secret.domain.OAuthTokens;
import de.hbt.salat.secret.domain.SecretStatus;
import de.hbt.salat.secret.domain.SecretSummary;
import de.hbt.salat.secret.domain.SecretValue;
import de.hbt.salat.secret.domain.Token;
import de.hbt.salat.secret.domain.UsernamePassword;
import de.hbt.salat.secret.service.OAuthService;
import de.hbt.salat.secret.service.SecretService;

/**
 * Maintaining the replication configs from the user interface (#984), with the scope resolved by
 * the ids of order and suborder (#1322).
 */
@FixedClock
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraReplicationConfigServiceTest {

  private static final long ID = 42L;
  private static final String STORED_PASSWORD = "stored-token";
  private static final long STORED_SECRET_ID = 500L;

  /** The order tree of these tests: ALPHA with A, A/01 below A, and B; the order BETA next to it. */
  private static final long ALPHA = 1L;
  private static final long BETA = 2L;
  private static final long A = 11L;
  private static final long A_01 = 12L;
  private static final long B = 13L;
  private static final long BETA_X = 21L;

  private static final Map<Long, String> ORDERS = Map.of(ALPHA, "ALPHA", BETA, "BETA");
  private static final Map<Long, SuborderLocation> SUBORDERS = Map.of(
      A, new SuborderLocation(A, ALPHA, List.of(A), "ALPHA/A"),
      A_01, new SuborderLocation(A_01, ALPHA, List.of(A, A_01), "ALPHA/A/01"),
      B, new SuborderLocation(B, ALPHA, List.of(B), "ALPHA/B"),
      BETA_X, new SuborderLocation(BETA_X, BETA, List.of(BETA_X), "BETA/X"));

  private JiraReplicationConfigService classUnderTest;

  @Mock
  private JiraReplicationConfigRepository configRepository;

  @Mock
  private JiraReplicationRunService jiraReplicationRunService;

  @Mock
  private JiraSearchClients jiraSearchClients;

  @Mock
  private JiraSearchClient jiraSearchClient;

  @Mock
  private CustomerorderService customerorderService;

  @Mock
  private SuborderService suborderService;

  @Mock
  private AuthorizedUser authorizedUser;

  @Mock
  private OrderReferences orderReferences;

  @Mock
  private JiraTicketRepository ticketRepository;

  /** The secret store as a map (#1432): what it keeps is what these tests read back. */
  @Mock
  private SecretService secretService;

  @Mock
  private OAuthService oauthService;

  private final Map<Long, SecretValue> secrets = new HashMap<>();
  private final Map<Long, Boolean> unreadable = new HashMap<>();

  @BeforeEach
  void setUp() {
    // the real resolution against a mocked order module: what is tested is the reading by id
    classUnderTest = new JiraReplicationConfigService(configRepository, jiraReplicationRunService,
        jiraSearchClients, new JiraScopes(customerorderService, suborderService), orderReferences, authorizedUser,
        ticketRepository, new JiraCredentialStore(secretService, oauthService));
    givenSecretStore();
    when(authorizedUser.isManager()).thenReturn(true);
    when(orderReferences.customerorder(anyLong())).thenAnswer(invocation ->
        customerorderWithId(invocation.getArgument(0)));
    when(orderReferences.suborder(any())).thenAnswer(invocation -> {
      Long suborderId = invocation.getArgument(0);
      return suborderId == null ? null
          : suborderWithId(suborderId, customerorderWithId(SUBORDERS.get(suborderId).customerorderId()));
    });
    when(customerorderService.getCustomerorderSignsByIds(any())).thenAnswer(invocation ->
        known(invocation.getArgument(0), ORDERS));
    when(suborderService.getSuborderLocationsByIds(any())).thenAnswer(invocation ->
        known(invocation.getArgument(0), SUBORDERS));
    when(suborderService.getCompleteOrderSignsByIds(any())).thenAnswer(invocation ->
        known(invocation.getArgument(0), SUBORDERS).entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().completeOrderSign())));
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

    assertThat(secretOf(saved())).isEqualTo(new UsernamePassword("jira-user", STORED_PASSWORD));
    verify(secretService, never()).replace(anyLong(), any());
  }

  @Test
  void a_filled_password_replaces_the_stored_one() {
    when(configRepository.findById(ID)).thenReturn(Optional.of(existingConfig()));

    classUnderTest.update(ID, data("  new-token  "));

    assertThat(saved().getSecretId()).isEqualTo(STORED_SECRET_ID);
    assertThat(secretOf(saved())).isEqualTo(new UsernamePassword("jira-user", "new-token"));
  }

  /** The password is stored encrypted, never in the replication (#1432). */
  @Test
  void a_new_replication_keeps_its_credentials_in_the_secret_store() {
    classUnderTest.create(data("pw"));

    assertThat(secretOf(saved())).isEqualTo(new UsernamePassword("jira-user", "pw"));
  }

  /** Without a key the password could only be kept in plain text, and that is never done (#1432). */
  @Test
  void without_a_key_no_replication_is_created() {
    when(secretService.isAvailable()).thenReturn(false);

    assertThatThrownBy(() -> classUnderTest.create(data("pw")))
        .isInstanceOf(BusinessRuleException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.SE_NO_KEY);
    verify(secretService, never()).create(any());
    verify(configRepository, never()).save(any());
  }

  /** The user name is part of the secret (#1432): a new one is written with the stored password. */
  @Test
  void a_new_user_name_keeps_the_stored_password() {
    when(configRepository.findById(ID)).thenReturn(Optional.of(existingConfig()));

    classUnderTest.update(ID, withAuth(JiraApiFlavor.SERVER, JiraAuthMethod.BASIC, " other-user ", null));

    assertThat(secretOf(saved())).isEqualTo(new UsernamePassword("other-user", STORED_PASSWORD));
  }

  @Test
  void a_new_user_name_needs_the_password_again_when_the_stored_one_is_unreadable() {
    when(configRepository.findById(ID)).thenReturn(Optional.of(existingConfig()));
    unreadable.put(STORED_SECRET_ID, true);

    assertThatThrownBy(() -> classUnderTest.update(ID,
        withAuth(JiraApiFlavor.SERVER, JiraAuthMethod.BASIC, "other-user", null)))
        .isInstanceOf(BusinessRuleException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.SE_SECRET_UNREADABLE);
    verify(secretService, never()).replace(anyLong(), any());
  }

  /** In a copy of the database the secret cannot be read: the form says so (#1432). */
  @Test
  void unreadable_credentials_are_reported_to_the_form() {
    when(configRepository.findById(ID)).thenReturn(Optional.of(existingConfig()));
    unreadable.put(STORED_SECRET_ID, true);

    var info = classUnderTest.getById(ID);

    assertThat(info.credentialsReadable()).isFalse();
    assertThat(info.username()).isNull();
  }

  @Test
  void entering_the_password_again_makes_unreadable_credentials_usable() {
    when(configRepository.findById(ID)).thenReturn(Optional.of(existingConfig()));
    unreadable.put(STORED_SECRET_ID, true);

    classUnderTest.update(ID, data("new-token"));

    assertThat(secretOf(saved())).isEqualTo(new UsernamePassword("jira-user", "new-token"));
  }

  /** Without a secret there is nothing to show, not even a user name (#1434). */
  @Test
  void a_replication_without_a_secret_shows_no_credentials() {
    var stored = existingConfig();
    stored.setSecretId(null);
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    var info = classUnderTest.getById(ID);

    assertThat(info.credentialsReadable()).isFalse();
    assertThat(info.username()).isNull();
  }

  @Test
  void entering_the_credentials_gives_a_replication_without_a_secret_one() {
    var stored = existingConfig();
    stored.setSecretId(null);
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, data("new-token"));

    assertThat(secretOf(saved())).isEqualTo(new UsernamePassword("jira-user", "new-token"));
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

  /** A Personal Access Token carries no user name (#1385); one left in the hidden field is dropped. */
  @Test
  void a_personal_access_token_on_server_is_stored_without_a_user_name() {
    classUnderTest.create(withAuth(JiraApiFlavor.SERVER, JiraAuthMethod.PERSONAL_ACCESS_TOKEN, "jira-user", "pat"));

    assertThat(saved().getAuthMethod()).isEqualTo(JiraAuthMethod.PERSONAL_ACCESS_TOKEN);
    assertThat(secretOf(saved())).isEqualTo(new Token("pat"));
  }

  @Test
  void a_personal_access_token_needs_no_user_name() {
    classUnderTest.create(withAuth(JiraApiFlavor.SERVER, JiraAuthMethod.PERSONAL_ACCESS_TOKEN, "  ", "pat"));

    assertThat(saved().getAuthMethod()).isEqualTo(JiraAuthMethod.PERSONAL_ACCESS_TOKEN);
  }

  @Test
  void basic_still_needs_a_user_name() {
    assertThatThrownBy(() -> classUnderTest.create(withAuth(JiraApiFlavor.SERVER, JiraAuthMethod.BASIC, "  ", "pw")))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_USERNAME_REQUIRED);
  }

  /** Atlassian Cloud accepts no bearer PAT; the form does not offer it, and the service refuses it. */
  @Test
  void a_personal_access_token_is_refused_on_cloud() {
    var data = withAuth(JiraApiFlavor.CLOUD, JiraAuthMethod.PERSONAL_ACCESS_TOKEN, null, "pat");

    assertThatThrownBy(() -> classUnderTest.create(data))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_TOKEN_NEEDS_SERVER);
    verify(configRepository, never()).save(any());
  }

  /** Kept across the switch, the stored password would go out as a bearer token. */
  @Test
  void switching_the_sign_in_method_needs_the_new_secret() {
    when(configRepository.findById(ID)).thenReturn(Optional.of(existingConfig()));
    var data = withAuth(JiraApiFlavor.SERVER, JiraAuthMethod.PERSONAL_ACCESS_TOKEN, null, null);

    assertThatThrownBy(() -> classUnderTest.update(ID, data))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_AUTH_CHANGE_NEEDS_SECRET);
    verify(configRepository, never()).save(any());
  }

  @Test
  void switching_the_sign_in_method_with_the_new_secret_succeeds() {
    when(configRepository.findById(ID)).thenReturn(Optional.of(existingConfig()));

    classUnderTest.update(ID, withAuth(JiraApiFlavor.SERVER, JiraAuthMethod.PERSONAL_ACCESS_TOKEN, null, "pat"));

    assertThat(saved().getAuthMethod()).isEqualTo(JiraAuthMethod.PERSONAL_ACCESS_TOKEN);
    assertThat(saved().getSecretId()).isEqualTo(STORED_SECRET_ID);
    assertThat(secretOf(saved())).isEqualTo(new Token("pat"));
  }

  @Test
  void an_existing_token_is_kept_when_the_field_stays_empty() {
    var stored = existingConfig();
    stored.setAuthMethod(JiraAuthMethod.PERSONAL_ACCESS_TOKEN);
    secrets.put(STORED_SECRET_ID, new Token(STORED_PASSWORD));
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withAuth(JiraApiFlavor.SERVER, JiraAuthMethod.PERSONAL_ACCESS_TOKEN, null, null));

    assertThat(secretOf(saved())).isEqualTo(new Token(STORED_PASSWORD));
    verify(secretService, never()).replace(anyLong(), any());
  }

  /** With OAuth (#1417) the account is connected after saving, so there is nothing to type. */
  @Test
  void an_oauth_replication_is_saved_without_a_secret() {
    classUnderTest.create(withAuth(JiraApiFlavor.CLOUD, JiraAuthMethod.OAUTH, null, null));

    assertThat(saved().getAuthMethod()).isEqualTo(JiraAuthMethod.OAUTH);
    assertThat(saved().getSecretId()).isNull();
    verify(secretService, never()).create(any());
  }

  @Test
  void an_oauth_replication_ignores_a_password_left_in_the_hidden_field() {
    classUnderTest.create(withAuth(JiraApiFlavor.CLOUD, JiraAuthMethod.OAUTH, "jira-user", "typed"));

    assertThat(saved().getSecretId()).isNull();
    verify(secretService, never()).create(any());
  }

  @Test
  void oauth_is_refused_on_server() {
    var data = withAuth(JiraApiFlavor.SERVER, JiraAuthMethod.OAUTH, null, null);

    assertThatThrownBy(() -> classUnderTest.create(data))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_OAUTH_NEEDS_CLOUD);
    verify(configRepository, never()).save(any());
  }

  /** Password and API token are of no use to OAuth and are not kept around. */
  @Test
  void switching_to_oauth_deletes_the_stored_credentials() {
    when(configRepository.findById(ID)).thenReturn(Optional.of(existingConfig()));

    classUnderTest.update(ID, withAuth(JiraApiFlavor.CLOUD, JiraAuthMethod.OAUTH, null, null));

    assertThat(saved().getAuthMethod()).isEqualTo(JiraAuthMethod.OAUTH);
    assertThat(saved().getSecretId()).isNull();
    verify(secretService).delete(STORED_SECRET_ID);
  }

  /** The OAuth tokens are no password: switching back needs the API token, as any switch does. */
  @Test
  void switching_from_oauth_to_the_api_token_needs_the_token() {
    var stored = connectedConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));
    var data = withAuth(JiraApiFlavor.CLOUD, JiraAuthMethod.BASIC, "jira-user@example.com", null);

    assertThatThrownBy(() -> classUnderTest.update(ID, data))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_AUTH_CHANGE_NEEDS_SECRET);
  }

  @Test
  void switching_from_oauth_to_the_api_token_replaces_the_tokens() {
    when(configRepository.findById(ID)).thenReturn(Optional.of(connectedConfig()));

    classUnderTest.update(ID, withAuth(JiraApiFlavor.CLOUD, JiraAuthMethod.BASIC, "jira-user@example.com", "api"));

    assertThat(saved().getSecretId()).isEqualTo(STORED_SECRET_ID);
    assertThat(secretOf(saved())).isEqualTo(new UsernamePassword("jira-user@example.com", "api"));
  }

  @Test
  void an_oauth_replication_keeps_its_connection_when_saved_again() {
    when(configRepository.findById(ID)).thenReturn(Optional.of(connectedConfig()));

    classUnderTest.update(ID, withAuth(JiraApiFlavor.CLOUD, JiraAuthMethod.OAUTH, null, null));

    assertThat(saved().getSecretId()).isEqualTo(STORED_SECRET_ID);
    assertThat(secretOf(saved())).isInstanceOf(OAuthTokens.class);
    verify(secretService, never()).delete(anyLong());
    verify(secretService, never()).replace(anyLong(), any());
  }

  @Test
  void the_form_sees_the_connected_account_but_no_token() {
    when(configRepository.findById(ID)).thenReturn(Optional.of(connectedConfig()));

    var info = classUnderTest.getById(ID);

    assertThat(info.oauthConnection().accountName()).isEqualTo("Person A");
    assertThat(info.oauthConnection().siteHost()).isEqualTo("jira.example.com");
    assertThat(info.oauthConnection().siteMatches()).isTrue();
    assertThat(info.oauthConnection().writeGranted()).isFalse();
    assertThat(info.toString()).doesNotContain("access-token").doesNotContain("refresh-token");
  }

  /** A config without a choice keeps HTTP Basic, the method every config used before #1385. */
  @Test
  void without_a_choice_the_sign_in_method_is_basic() {
    classUnderTest.create(withAuth(JiraApiFlavor.SERVER, null, "jira-user", "pw"));

    assertThat(saved().getAuthMethod()).isEqualTo(JiraAuthMethod.BASIC);
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
    assertThat(secretOf(saved())).isEqualTo(new UsernamePassword("jira-user", STORED_PASSWORD));
  }

  @Test
  void a_replication_without_a_jql_query_could_only_ever_fail() {
    var withoutJql = new JiraReplicationConfigData("Alpha", ALPHA, null, "https://jira.example.com",
        JiraApiFlavor.SERVER, JiraAuthMethod.BASIC, "jira-user", "token", "  ", null, null, null, null, true, false, null, false);

    assertThatThrownBy(() -> classUnderTest.create(withoutJql))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_JQL_REQUIRED);
  }

  @Test
  void a_base_url_without_a_scheme_is_rejected() {
    var badUrl = new JiraReplicationConfigData("Alpha", ALPHA, null, "jira.example.com",
        JiraApiFlavor.SERVER, JiraAuthMethod.BASIC, "jira-user", "token", "project = ALPHA", null, null, null, null, true, false, null, false);

    assertThatThrownBy(() -> classUnderTest.create(badUrl))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_BASE_URL_INVALID);
  }

  @Test
  void a_page_size_of_zero_or_less_is_rejected() {
    var zeroPageSize = new JiraReplicationConfigData("Alpha", ALPHA, null, "https://jira.example.com",
        JiraApiFlavor.SERVER, JiraAuthMethod.BASIC, "jira-user", "token", "project = ALPHA", null, null, null, 0, true, false, null, false);

    assertThatThrownBy(() -> classUnderTest.create(zeroPageSize))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_PAGE_SIZE_INVALID);
  }

  @Test
  void a_missing_flavor_is_stored_as_server() {
    // What a row without an explicit flavor has always meant.
    classUnderTest.create(new JiraReplicationConfigData("Alpha", ALPHA, null, "https://jira.example.com",
        null, null, "jira-user", "token", "project = ALPHA", null, null, null, null, true, false, null, false));

    assertThat(saved().getApiFlavor()).isEqualTo(JiraApiFlavor.SERVER);
  }

  @Test
  void switching_the_worklog_sync_on_without_a_date_starts_today() {
    // The first run must not carry the whole history of the order into JIRA (#1007).
    classUnderTest.create(withWorklogSync(ALPHA, null, true, null));

    assertThat(saved().getWorklogSyncFrom()).isEqualTo(DateUtils.today());
  }

  @Test
  void a_start_date_that_was_entered_is_kept() {
    // Moving it back is how a period is filled in afterwards, on purpose.
    var backfill = LocalDate.of(2026, 1, 1);

    classUnderTest.create(withWorklogSync(ALPHA, null, true, backfill));

    assertThat(saved().getWorklogSyncFrom()).isEqualTo(backfill);
  }

  @Test
  void a_replication_without_the_worklog_sync_gets_no_start_date() {
    classUnderTest.create(withWorklogSync(ALPHA, null, false, null));

    assertThat(saved().getWorklogSyncFrom()).isNull();
  }

  @Test
  void switching_the_worklog_sync_off_keeps_the_start_date() {
    // What SALAT wrote stays in JIRA and stays remembered; switching on again picks up where it
    // left off instead of starting a second period next to the first.
    var stored = existingConfig();
    stored.setWorklogSyncEnabled(true);
    stored.setWorklogSyncFrom(LocalDate.of(2026, 1, 1));
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withWorklogSync(ALPHA, null, false, LocalDate.of(2026, 1, 1)));

    assertThat(stored.getWorklogSyncFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(stored.getWorklogSyncEnabled()).isFalse();
  }

  @Test
  void the_restriction_to_invoiceable_bookings_is_stored() {
    classUnderTest.create(withInvoiceableOnly(true));

    assertThat(saved().getWorklogSyncInvoiceableOnly()).isTrue();
  }

  @Test
  void a_replication_without_the_restriction_writes_every_booking() {
    classUnderTest.create(withInvoiceableOnly(false));

    assertThat(saved().getWorklogSyncInvoiceableOnly()).isFalse();
  }

  @Test
  void the_restriction_can_be_lifted_again() {
    var stored = existingConfig();
    stored.setWorklogSyncEnabled(true);
    stored.setWorklogSyncFrom(LocalDate.of(2026, 1, 1));
    stored.setWorklogSyncInvoiceableOnly(true);
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withInvoiceableOnly(false));

    assertThat(stored.getWorklogSyncInvoiceableOnly()).isFalse();
  }

  @Test
  void a_second_worklog_sync_over_the_same_branch_of_the_same_instance_is_refused() {
    // An order-wide replication and one for a suborder inside it would each write their own worklog
    // on the same ticket and day — the time would stand twice in JIRA, and nothing in SALAT shows
    // it.
    givenOtherReplication(ALPHA, A, "https://jira.example.com", true);

    assertThatThrownBy(() -> classUnderTest.create(withWorklogSync(ALPHA, null, true, null)))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_WORKLOG_SCOPE_OVERLAP);
  }

  @Test
  void a_suborder_below_one_with_a_worklog_sync_overlaps_it() {
    givenOtherReplication(ALPHA, A, "https://jira.example.com", true);

    assertThatThrownBy(() -> classUnderTest.create(withWorklogSync(ALPHA, A_01, true, null)))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_WORKLOG_SCOPE_OVERLAP);
  }

  @Test
  void a_suborder_above_one_with_a_worklog_sync_overlaps_it() {
    givenOtherReplication(ALPHA, A_01, "https://jira.example.com", true);

    assertThatThrownBy(() -> classUnderTest.create(withWorklogSync(ALPHA, A, true, null)))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_WORKLOG_SCOPE_OVERLAP);
  }

  /**
   * Two replications of exactly the same scope each write to the tickets they maintain (#1386), and a
   * key belongs to one ticket of the scope: no ticket and day gets a worklog from both.
   */
  @Test
  void a_second_worklog_sync_of_exactly_the_same_scope_is_allowed() {
    givenOtherReplication(ALPHA, A, "https://jira.example.com", true);

    classUnderTest.create(withWorklogSync(ALPHA, A, true, null));

    assertThat(saved().getWorklogSyncEnabled()).isTrue();
  }

  @Test
  void a_second_order_wide_worklog_sync_of_the_same_order_is_allowed() {
    givenOtherReplication(ALPHA, null, "https://jira.example.com", true);

    classUnderTest.create(withWorklogSync(ALPHA, null, true, null));

    assertThat(saved().getWorklogSyncEnabled()).isTrue();
  }

  @Test
  void two_sibling_branches_do_not_overlap() {
    givenOtherReplication(ALPHA, A_01, "https://jira.example.com", true);

    classUnderTest.create(withWorklogSync(ALPHA, B, true, null));

    assertThat(saved().getWorklogSyncEnabled()).isTrue();
  }

  @Test
  void the_same_branch_on_another_jira_instance_is_allowed() {
    // Different installations share no issue keys, so there is nothing to collide.
    givenOtherReplication(ALPHA, A, "https://other-jira.example.com", true);

    classUnderTest.create(withWorklogSync(ALPHA, null, true, null));

    assertThat(saved().getWorklogSyncEnabled()).isTrue();
  }

  @Test
  void an_overlapping_replication_that_writes_no_worklogs_is_no_obstacle() {
    givenOtherReplication(ALPHA, A, "https://jira.example.com", false);

    classUnderTest.create(withWorklogSync(ALPHA, null, true, null));

    assertThat(saved().getWorklogSyncEnabled()).isTrue();
  }

  @Test
  void a_replication_does_not_collide_with_itself_when_it_is_edited() {
    var stored = existingConfig();
    setId(stored, ID);
    stored.setWorklogSyncEnabled(true);
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));
    when(configRepository.findAllByOrderByNameAsc()).thenReturn(List.of(stored));

    classUnderTest.update(ID, withWorklogSync(ALPHA, null, true, null));

    assertThat(stored.getWorklogSyncEnabled()).isTrue();
  }

  @Test
  void two_orders_never_overlap() {
    // Different customer orders share no suborder — whatever their signs look like (#1322).
    givenOtherReplication(BETA, null, "https://jira.example.com", true);

    classUnderTest.create(withWorklogSync(ALPHA, null, true, null));

    assertThat(saved().getWorklogSyncEnabled()).isTrue();
  }

  @Test
  void deleting_a_replication_leaves_the_replicated_tickets_alone() {
    // jira_ticket hangs off the scope, not off the config — the rows are not wrong, only no
    // longer kept up to date.
    var stored = existingConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.delete(ID);

    verify(configRepository).delete(stored);
  }

  @Test
  void deleting_a_replication_takes_its_runs_with_it() {
    // a run nobody can name any more says nothing (#1282)
    when(configRepository.findById(ID)).thenReturn(Optional.of(existingConfig()));

    classUnderTest.delete(ID);

    verify(jiraReplicationRunService).deleteRunsOf(ID);
  }

  /** A deleted secret is gone; one left behind would still be stored (#1432, ADR-0038 §6). */
  @Test
  void deleting_a_replication_deletes_its_secret_after_it() {
    var stored = existingConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.delete(ID);

    var order = inOrder(configRepository, secretService);
    order.verify(configRepository).delete(stored);
    order.verify(secretService).delete(STORED_SECRET_ID);
    assertThat(secrets).doesNotContainKey(STORED_SECRET_ID);
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
    assertThatThrownBy(() -> classUnderTest.getSelectableFields(ID)).isInstanceOf(AuthorizationException.class);

    verifyNoInteractions(configRepository, jiraReplicationRunService, jiraSearchClients,
        customerorderService, suborderService);
  }

  @Test
  void changing_the_field_list_resets_the_watermark() {
    // Otherwise the search keeps every already replicated ticket out and the newly configured
    // fields reach nothing but the tickets edited in JIRA afterwards (#881).
    var stored = existingConfig();
    stored.setLastMaxUpdated(LocalDateTime.of(2026, 6, 1, 8, 0));
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withFields("customfield_10123", "customfield_10123"));

    assertThat(saved().getLastMaxUpdated()).isNull();
    assertThat(saved().getAdditionalFieldNames()).isEqualTo("customfield_10123");
    assertThat(saved().getInheritedFieldNames()).isEqualTo("customfield_10123");
  }

  @Test
  void an_edit_that_leaves_the_field_list_alone_keeps_the_watermark() {
    var watermark = LocalDateTime.of(2026, 6, 1, 8, 0);
    var stored = existingConfig();
    stored.setLastMaxUpdated(watermark);
    stored.setAdditionalFieldNames("customfield_10123");
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withFields(" customfield_10123 ", null));

    assertThat(saved().getLastMaxUpdated()).isEqualTo(watermark);
  }

  @Test
  void changing_the_jql_resets_the_watermark() {
    // Only a run without the watermark sees every ticket the JQL matches (#1167): after narrowing it
    // removes the ones left out, after widening it fetches the older ones that match now.
    var stored = existingConfig();
    stored.setLastMaxUpdated(LocalDateTime.of(2026, 6, 1, 8, 0));
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withJql("project = ALPHA AND component = Web"));

    assertThat(saved().getLastMaxUpdated()).isNull();
    assertThat(saved().getJql()).isEqualTo("project = ALPHA AND component = Web");
  }

  @Test
  void an_edit_that_leaves_the_jql_alone_keeps_the_watermark() {
    var watermark = LocalDateTime.of(2026, 6, 1, 8, 0);
    var stored = existingConfig();
    stored.setLastMaxUpdated(watermark);
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    // the text is trimmed before it is stored, so surrounding blanks are no change
    classUnderTest.update(ID, withJql("  project = ALPHA "));

    assertThat(saved().getLastMaxUpdated()).isEqualTo(watermark);
  }

  @Test
  void a_replication_can_be_scoped_to_one_suborder_of_any_depth() {
    classUnderTest.create(withScope(ALPHA, A_01, "token"));

    assertThat(saved().getCustomerorderId()).isEqualTo(ALPHA);
    assertThat(saved().getSuborderId()).isEqualTo(A_01);
  }

  @Test
  void a_suborder_of_another_order_is_refused() {
    // otherwise the replication would read the bookings of one order and claim to be another's
    assertThatThrownBy(() -> classUnderTest.create(withScope(ALPHA, BETA_X, "token")))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_SCOPE_NOT_FOUND);

    verify(configRepository, never()).save(any());
  }

  @Test
  void an_unknown_suborder_is_refused() {
    assertThatThrownBy(() -> classUnderTest.create(withScope(ALPHA, 999L, "token")))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_SCOPE_NOT_FOUND);

    verify(configRepository, never()).save(any());
  }

  @Test
  void an_unknown_customer_order_is_refused_just_the_same() {
    assertThatThrownBy(() -> classUnderTest.create(withScope(999L, null, "token")))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_SCOPE_NOT_FOUND);
  }

  @Test
  void a_replication_without_a_scope_is_refused_before_anything_is_looked_up() {
    assertThatThrownBy(() -> classUnderTest.create(withScope(null, null, "token")))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_SCOPE_REQUIRED);
    verifyNoInteractions(customerorderService, suborderService);
  }

  @Test
  void moving_a_replication_to_another_scope_resets_the_watermark() {
    // the new scope has no tickets of its own yet, and with the watermark in place the search would
    // only ever find what JIRA has touched since (#1025)
    var stored = existingConfig();
    stored.setLastMaxUpdated(LocalDateTime.of(2026, 6, 1, 8, 0));
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withScope(ALPHA, A_01, null));

    assertThat(saved().getSuborderId()).isEqualTo(A_01);
    assertThat(saved().getLastMaxUpdated()).isNull();
  }

  /**
   * The tickets left in the old scope are released (#1386), as if the replication had been deleted:
   * otherwise they stay locked against editing by hand, are never removed, and a replication of the
   * old scope skips them as maintained by another one.
   */
  @Test
  void moving_a_replication_to_another_scope_releases_its_tickets() {
    var stored = existingConfig();
    setId(stored, ID);
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withScope(ALPHA, A_01, null));

    verify(ticketRepository).releaseFromReplication(ID);
  }

  @Test
  void an_edit_that_leaves_the_scope_alone_keeps_its_tickets() {
    var stored = existingConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withScope(ALPHA, null, null));

    verify(ticketRepository, never()).releaseFromReplication(anyLong());
  }

  @Test
  void an_edit_that_leaves_the_scope_alone_keeps_the_watermark() {
    var watermark = LocalDateTime.of(2026, 6, 1, 8, 0);
    var stored = existingConfig();
    stored.setLastMaxUpdated(watermark);
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withScope(ALPHA, null, null));

    assertThat(saved().getLastMaxUpdated()).isEqualTo(watermark);
  }

  @Test
  void a_renamed_order_is_still_the_same_scope() {
    // the stored reference still carries the old sign; what decides is the id (#1322, #1368)
    var watermark = LocalDateTime.of(2026, 6, 1, 8, 0);
    var stored = existingConfig();
    stored.getCustomerorder().setSign("ALPHA-OLD");
    stored.setLastMaxUpdated(watermark);
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withScope(ALPHA, null, null));

    assertThat(saved().getLastMaxUpdated()).isEqualTo(watermark);
    assertThat(saved().getCustomerorderId()).isEqualTo(ALPHA);
  }

  @Test
  void the_list_names_the_scope_as_the_order_tree_carries_it_now() {
    // asked of the order module by id (#1322), one query for all configs
    var stored = existingConfig();
    setId(stored, ID);
    stored.setSuborder(suborderWithId(A_01, stored.getCustomerorder()));
    when(configRepository.findAllByOrderByNameAsc()).thenReturn(List.of(stored));
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    assertThat(classUnderTest.getAll()).singleElement()
        .satisfies(info -> assertThat(info.scopeSign()).isEqualTo("ALPHA/A/01"))
        .satisfies(info -> assertThat(info.suborderId()).isEqualTo(A_01));
    assertThat(classUnderTest.getById(ID).scopeSign()).isEqualTo("ALPHA/A/01");
  }

  @Test
  void the_field_catalogue_is_fetched_with_the_stored_credentials() {
    // never with values from the form - otherwise a manager could point the server at any address
    // it can reach and get the authenticated answer shown back
    var stored = existingConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));
    when(jiraSearchClients.forFlavor(JiraApiFlavor.SERVER)).thenReturn(jiraSearchClient);
    when(jiraSearchClient.listFields(any())).thenReturn(List.of());

    classUnderTest.getSelectableFields(ID);

    var request = ArgumentCaptor.forClass(JiraFieldsRequest.class);
    verify(jiraSearchClient).listFields(request.capture());
    assertThat(request.getValue().baseUrl()).isEqualTo("https://jira.example.com");
    assertThat(request.getValue().credentials().username()).isEqualTo("jira-user");
    assertThat(request.getValue().credentials().secret()).isEqualTo(STORED_PASSWORD);
  }

  @Test
  void a_cascading_select_is_offered_with_its_second_level() {
    givenCatalogue(
        field("customfield_10200", "Kategorie", "com.atlassian…customfieldtypes:cascadingselect", null),
        field("customfield_10123", "Abrechnung", "com.atlassian…customfieldtypes:select", null),
        field("duedate", "Fälligkeitsdatum", null, "date"));

    var catalogue = classUnderTest.getSelectableFields(ID);

    // sorted by name, and the second level sits right behind the field it belongs to
    assertThat(catalogue.options()).extracting(JiraFieldOption::key).containsExactly(
        "customfield_10123", "duedate", "customfield_10200", "customfield_10200.child.value");
    assertThat(catalogue.options()).extracting(JiraFieldOption::type).containsExactly(
        "select", "date", "cascadingselect", "cascadingselect");
    assertThat(catalogue.options().get(3).secondLevel()).isTrue();
    assertThat(catalogue.hasError()).isFalse();
  }

  @Test
  void an_umlaut_sorts_where_a_reader_looks_for_it() {
    // comparing code points would file this behind "Zeiterfassung", which reads as broken in a list
    // somebody scans by name
    givenCatalogue(
        field("customfield_10300", "Zeiterfassung", null, "string"),
        field("customfield_10301", "Änderungsdatum", null, "date"));

    assertThat(classUnderTest.getSelectableFields(ID).options())
        .extracting(JiraFieldOption::name)
        .containsExactly("Änderungsdatum", "Zeiterfassung");
  }

  @Test
  void a_field_without_a_name_falls_back_to_its_key() {
    givenCatalogue(field("customfield_10400", null, null, "string"));

    assertThat(classUnderTest.getSelectableFields(ID).options())
        .extracting(JiraFieldOption::name).containsExactly("customfield_10400");
  }

  @Test
  void a_failed_catalogue_is_reported_back_without_the_password_in_it() {
    var stored = existingConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));
    when(jiraSearchClients.forFlavor(JiraApiFlavor.SERVER)).thenReturn(jiraSearchClient);
    when(jiraSearchClient.listFields(any()))
        .thenThrow(new IllegalStateException("401 for user:" + STORED_PASSWORD));

    var catalogue = classUnderTest.getSelectableFields(ID);

    assertThat(catalogue.hasError()).isTrue();
    assertThat(catalogue.errorMessage()).doesNotContain(STORED_PASSWORD).contains("***");
    assertThat(catalogue.options()).isEmpty();
  }

  /** Said in words by the form and the picker (#1432), not a failed request to JIRA. */
  @Test
  void the_field_catalogue_is_not_fetched_with_unreadable_credentials() {
    when(configRepository.findById(ID)).thenReturn(Optional.of(existingConfig()));
    unreadable.put(STORED_SECRET_ID, true);

    assertThatThrownBy(() -> classUnderTest.getSelectableFields(ID))
        .isInstanceOf(BusinessRuleException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.SE_SECRET_UNREADABLE);
    verifyNoInteractions(jiraSearchClients);
  }

  private void givenCatalogue(JiraField... fields) {
    when(configRepository.findById(ID)).thenReturn(Optional.of(existingConfig()));
    when(jiraSearchClients.forFlavor(JiraApiFlavor.SERVER)).thenReturn(jiraSearchClient);
    when(jiraSearchClient.listFields(any())).thenReturn(List.of(fields));
  }

  private static JiraField field(String id, String name, String customType, String type) {
    var field = new JiraField();
    field.setId(id);
    field.setName(name);
    var schema = new JiraField.Schema();
    schema.setCustom(customType);
    schema.setType(type);
    field.setSchema(schema);
    return field;
  }

  private static JiraReplicationConfigData withJql(String jql) {
    return new JiraReplicationConfigData("Alpha", ALPHA, null, "https://jira.example.com",
        JiraApiFlavor.SERVER, JiraAuthMethod.BASIC, "jira-user", null, jql, null, null, null, 100, true, false, null, false);
  }

  private static JiraReplicationConfigData withFields(String additional, String inherited) {
    return new JiraReplicationConfigData("Alpha", ALPHA, null, "https://jira.example.com",
        JiraApiFlavor.SERVER, JiraAuthMethod.BASIC, "jira-user", null, "project = ALPHA", null, additional, inherited,
        100, true, false, null, false);
  }

  /** A Cloud replication connected to an Atlassian account (#1417). */
  private JiraReplicationConfig connectedConfig() {
    var config = existingConfig();
    config.setApiFlavor(JiraApiFlavor.CLOUD);
    config.setAuthMethod(JiraAuthMethod.OAUTH);
    secrets.put(STORED_SECRET_ID, new OAuthTokens("access-token", Instant.parse("2026-06-25T11:00:00Z"),
        "refresh-token", new OAuthConnection("atlassian", "account-1", "Person A", "cloud-1",
            "https://jira.example.com", Set.of("read:jira-work"), "mgr", LocalDateTime.of(2026, 6, 1, 9, 0))));
    return config;
  }

  private JiraReplicationConfig existingConfig() {
    var config = new JiraReplicationConfig();
    config.setName("Alpha");
    config.setCustomerorder(customerorderWithId(ALPHA));
    config.setBaseUrl("https://jira.example.com");
    config.setApiFlavor(JiraApiFlavor.SERVER);
    config.setSecretId(STORED_SECRET_ID);
    secrets.put(STORED_SECRET_ID, new UsernamePassword("jira-user", STORED_PASSWORD));
    config.setJql("project = ALPHA");
    config.setEnabled(true);
    return config;
  }

  private static JiraReplicationConfigData withScope(Long customerorderId, Long suborderId, String password) {
    return new JiraReplicationConfigData("Alpha", customerorderId, suborderId, "https://jira.example.com",
        JiraApiFlavor.SERVER, JiraAuthMethod.BASIC, "jira-user", password, "project = ALPHA", null, null, null, 100, true, false, null, false);
  }

  private static JiraReplicationConfigData withWorklogSync(long customerorderId, Long suborderId,
                                                           boolean enabled, LocalDate from) {
    return new JiraReplicationConfigData("Alpha", customerorderId, suborderId, "https://jira.example.com",
        JiraApiFlavor.SERVER, JiraAuthMethod.BASIC, "jira-user", "token", "project = ALPHA", null, null, null, 100, true,
        enabled, from, false);
  }

  private static JiraReplicationConfigData withInvoiceableOnly(boolean invoiceableOnly) {
    return new JiraReplicationConfigData("Alpha", ALPHA, null, "https://jira.example.com",
        JiraApiFlavor.SERVER, JiraAuthMethod.BASIC, "jira-user", "token", "project = ALPHA", null, null, null, 100, true,
        true, LocalDate.of(2026, 1, 1), invoiceableOnly);
  }

  private void givenOtherReplication(long customerorderId, Long suborderId, String baseUrl,
                                     boolean worklogSync) {
    var other = new JiraReplicationConfig();
    setId(other, 99L);
    other.setName("Beta");
    other.setCustomerorder(customerorderWithId(customerorderId));
    other.setSuborder(suborderId == null ? null : suborderWithId(suborderId, other.getCustomerorder()));
    other.setBaseUrl(baseUrl);
    other.setWorklogSyncEnabled(worklogSync);
    when(configRepository.findAllByOrderByNameAsc()).thenReturn(List.of(other));
  }

  private static JiraReplicationConfigData data(String password) {
    return new JiraReplicationConfigData("Alpha", ALPHA, null, "https://jira.example.com",
        JiraApiFlavor.SERVER, JiraAuthMethod.BASIC, "jira-user", password, "project = ALPHA", null, null, null, 100, true, false, null, false);
  }

  private static JiraReplicationConfigData withAuth(JiraApiFlavor apiFlavor, JiraAuthMethod authMethod,
                                                    String username, String password) {
    return new JiraReplicationConfigData("Alpha", ALPHA, null, "https://jira.example.com", apiFlavor,
        authMethod, username, password, "project = ALPHA", null, null, null, 100, true, false, null, false);
  }

  /**
   * A map behind the mocked store: create hands out ids, replace and delete act on the map, and a
   * secret marked unreadable fails to read the way one from another environment does.
   */
  private void givenSecretStore() {
    when(secretService.isAvailable()).thenReturn(true);
    when(secretService.create(any())).thenAnswer(invocation -> {
      var id = 1000L + secrets.size();
      secrets.put(id, invocation.getArgument(0));
      return id;
    });
    doAnswer(invocation -> {
      secrets.put(invocation.getArgument(0), invocation.getArgument(1));
      unreadable.remove((Long) invocation.getArgument(0));
      return null;
    }).when(secretService).replace(anyLong(), any());
    doAnswer(invocation -> secrets.remove((Long) invocation.getArgument(0)))
        .when(secretService).delete(anyLong());
    when(secretService.read(anyLong())).thenAnswer(invocation -> {
      long id = invocation.getArgument(0);
      if (unreadable.containsKey(id)) {
        throw new BusinessRuleException(ErrorCode.SE_SECRET_UNREADABLE);
      }
      return secrets.get(id);
    });
    when(secretService.getSummary(anyLong())).thenAnswer(invocation -> {
      long id = invocation.getArgument(0);
      var value = secrets.get(id);
      var readable = !unreadable.containsKey(id);
      var username = readable && value instanceof UsernamePassword usernamePassword ? usernamePassword.username() : null;
      var connection = readable && value instanceof OAuthTokens tokens ? tokens.connection() : null;
      return new SecretSummary(id, value.type(), SecretStatus.VALID, readable, username, connection);
    });
  }

  private SecretValue secretOf(JiraReplicationConfig config) {
    return secrets.get(config.getSecretId());
  }

  private JiraReplicationConfig saved() {
    var captor = ArgumentCaptor.forClass(JiraReplicationConfig.class);
    verify(configRepository, atLeastOnce()).save(captor.capture());
    return captor.getValue();
  }

  /** What the order module answers for these ids: the known ones, an unknown id is missing. */
  private static <T> Map<Long, T> known(Collection<Long> ids, Map<Long, T> tree) {
    return ids.stream().filter(tree::containsKey).collect(Collectors.toMap(id -> id, tree::get));
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
