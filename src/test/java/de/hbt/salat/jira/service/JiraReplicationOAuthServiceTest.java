package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.jira.domain.JiraApiFlavor;
import de.hbt.salat.jira.domain.JiraAuthMethod;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.oauth.JiraOAuthGrant;
import de.hbt.salat.jira.oauth.JiraOAuthService;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.secret.domain.OAuthTokens;
import de.hbt.salat.secret.service.SecretService;

/**
 * Connecting a replication to an Atlassian account (#1417): what the replication decides — who may,
 * which scopes, which site — around the flow of the module {@code secret}, which is mocked here.
 */
@FixedClock
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraReplicationOAuthServiceTest {

  private static final long ID = 7L;
  private static final String SITE = "https://example.atlassian.net";

  @Mock
  private JiraReplicationConfigRepository configRepository;

  @Mock
  private SecretService secretService;

  @Mock
  private JiraOAuthService oauthService;

  @Mock
  private AtlassianAccountClient accountClient;

  @Mock
  private AuthorizedUser authorizedUser;

  private JiraReplicationConfig config;
  private JiraReplicationOAuthService classUnderTest;

  @BeforeEach
  void setUp() {
    classUnderTest = new JiraReplicationOAuthService(configRepository, new JiraCredentialStore(secretService, oauthService),
        oauthService, accountClient, authorizedUser, mock(PlatformTransactionManager.class));
    when(authorizedUser.isManager()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("mgr");
    config = new JiraReplicationConfig();
    config.setName("Alpha");
    config.setBaseUrl(SITE + "/");
    config.setApiFlavor(JiraApiFlavor.CLOUD);
    config.setAuthMethod(JiraAuthMethod.OAUTH);
    config.setWorklogSyncEnabled(false);
    when(configRepository.findById(ID)).thenReturn(Optional.of(config));
    when(oauthService.complete(any(), any(), any(), any())).thenReturn(
        new JiraOAuthGrant("atlassian", "access-1", Instant.parse("2026-06-25T09:15:30Z"), "refresh-1",
            Set.of("read:jira-work", "read:me", "offline_access")));
    when(accountClient.me("access-1")).thenReturn(new AtlassianAccountClient.Account("account-1", "Person A", null));
    when(secretService.create(any())).thenReturn(500L);
  }

  @Test
  void a_replication_without_worklogs_asks_for_reading_only() {
    classUnderTest.startConnection(ID);

    verify(oauthService).authorize(eq("jira-replication:7"),
        eq(List.of("read:jira-work", "read:me", "offline_access")), anyMap());
  }

  @Test
  void a_replication_that_writes_worklogs_asks_for_writing_as_well() {
    config.setWorklogSyncEnabled(true);

    classUnderTest.startConnection(ID);

    verify(oauthService).authorize(eq("jira-replication:7"),
        eq(List.of("read:jira-work", "read:me", "offline_access", "write:jira-work")), anyMap());
  }

  @Test
  void the_authorization_goes_to_the_atlassian_api_with_consent() {
    classUnderTest.startConnection(ID);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<java.util.Map<String, String>> parameters = ArgumentCaptor.forClass(java.util.Map.class);
    verify(oauthService).authorize(any(), any(), parameters.capture());
    assertThat(parameters.getValue()).containsEntry("audience", "api.atlassian.com").containsEntry("prompt", "consent");
  }

  /** Connecting works on the stored replication; an unsaved switch to OAuth does not count. */
  @Test
  void a_stored_replication_with_another_method_cannot_be_connected() {
    config.setAuthMethod(JiraAuthMethod.BASIC);

    assertRejected(() -> classUnderTest.startConnection(ID), ErrorCode.JI_REPLICATION_OAUTH_NOT_SELECTED);
    verify(oauthService, never()).authorize(any(), any(), anyMap());
  }

  @Test
  void the_callback_names_the_replication_of_the_attempt() {
    when(oauthService.ownerOf("cookie", "state")).thenReturn("jira-replication:7");

    assertThat(classUnderTest.replicationOf("cookie", "state")).isEqualTo(ID);
  }

  @Test
  void a_callback_for_another_owner_is_rejected() {
    when(oauthService.ownerOf("cookie", "state")).thenReturn("person:7");

    assertRejected(() -> classUnderTest.replicationOf("cookie", "state"), ErrorCode.JI_REPLICATION_OAUTH_STATE_INVALID);
  }

  @Test
  void connecting_stores_the_tokens_with_account_site_and_who_connected() {
    when(accountClient.accessibleSites("access-1")).thenReturn(List.of(
        new AtlassianAccountClient.Site("cloud-other", "Other", "https://other.atlassian.net", List.of("read:jira-work")),
        new AtlassianAccountClient.Site("cloud-1", "Example", SITE, List.of("read:jira-work", "read:me"))));

    classUnderTest.completeConnection(ID, "cookie", "state", "code", null);

    var stored = ArgumentCaptor.forClass(OAuthTokens.class);
    verify(secretService).create(stored.capture());
    var connection = stored.getValue().connection();
    assertThat(stored.getValue().refreshToken()).isEqualTo("refresh-1");
    assertThat(connection.provider()).isEqualTo("atlassian");
    assertThat(connection.accountId()).isEqualTo("account-1");
    assertThat(connection.accountName()).isEqualTo("Person A");
    assertThat(connection.resourceId()).isEqualTo("cloud-1");
    assertThat(connection.resourceUrl()).isEqualTo(SITE);
    assertThat(connection.connectedBy()).isEqualTo("mgr");
    assertThat(connection.connectedAt()).isNotNull();
    assertThat(config.getSecretId()).isEqualTo(500L);
    verify(configRepository).save(config);
  }

  @Test
  void connecting_again_replaces_the_tokens_of_the_replication() {
    config.setSecretId(500L);
    when(accountClient.accessibleSites("access-1")).thenReturn(List.of(
        new AtlassianAccountClient.Site("cloud-1", "Example", SITE, List.of("read:jira-work"))));

    classUnderTest.completeConnection(ID, "cookie", "state", "code", null);

    verify(secretService).replace(eq(500L), any(OAuthTokens.class));
    verify(secretService, never()).create(any());
  }

  @Test
  void an_account_that_does_not_reach_the_site_of_the_base_url_stores_nothing() {
    when(accountClient.accessibleSites("access-1")).thenReturn(List.of(
        new AtlassianAccountClient.Site("cloud-other", "Other", "https://other.atlassian.net", List.of("read:jira-work"))));

    assertRejected(() -> classUnderTest.completeConnection(ID, "cookie", "state", "code", null),
        ErrorCode.JI_REPLICATION_OAUTH_SITE_NOT_ACCESSIBLE);
    verify(secretService, never()).create(any());
    verify(secretService, never()).replace(anyLong(), any());
    assertThat(config.getSecretId()).isNull();
  }

  @Test
  void disconnecting_deletes_the_tokens_and_forgets_them() {
    config.setSecretId(500L);

    classUnderTest.disconnect(ID);

    verify(secretService).delete(500L);
    assertThat(config.getSecretId()).isNull();
    verify(configRepository).save(config);
  }

  /** Only who may edit the replication may connect, connect again or disconnect it — the management. */
  @Test
  void only_the_management_connects_and_disconnects() {
    when(authorizedUser.isManager()).thenReturn(false);

    assertThatThrownBy(() -> classUnderTest.startConnection(ID)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.replicationOf("cookie", "state")).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.completeConnection(ID, "cookie", "state", "code", null))
        .isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.disconnect(ID)).isInstanceOf(AuthorizationException.class);
    verifyNoInteractions(oauthService, secretService);
  }

  @Test
  void the_service_is_management_only_for_the_aspect_as_well() {
    var authorized = JiraReplicationOAuthService.class.getAnnotation(de.hbt.salat.auth.domain.Authorized.class);

    assertThat(authorized.requiresManager()).isTrue();
  }

  private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(ex -> assertThat(((ErrorCodeException) ex).getMessages())
            .anySatisfy(message -> assertThat(message.getErrorCode()).isEqualTo(code)));
  }
}
