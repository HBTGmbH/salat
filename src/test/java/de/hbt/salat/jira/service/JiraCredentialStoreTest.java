package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.jira.domain.JiraApiFlavor;
import de.hbt.salat.jira.domain.JiraAuthMethod;
import de.hbt.salat.jira.domain.JiraOAuthConnection;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.oauth.JiraOAuthService;
import de.hbt.salat.secret.service.SecretService;

/** The credentials of a replication connected via OAuth (#1417): a fresh access token and the API address of its site. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraCredentialStoreTest {

  @Mock
  private SecretService secretService;

  @Mock
  private JiraOAuthService oauthService;

  private JiraCredentialStore store;
  private JiraReplicationConfig config;

  @BeforeEach
  void setUp() {
    store = new JiraCredentialStore(secretService, oauthService);
    when(oauthService.isAvailable()).thenReturn(true);
    config = new JiraReplicationConfig();
    config.setBaseUrl("https://Example.atlassian.net/");
    config.setApiFlavor(JiraApiFlavor.CLOUD);
    config.setAuthMethod(JiraAuthMethod.OAUTH);
    config.setSecretId(500L);
    when(oauthService.accessToken(500L)).thenReturn("access-1");
  }

  @Test
  void a_connected_replication_calls_the_atlassian_api_of_its_site_with_the_access_token() {
    config.setOauthConnection(connection("https://example.atlassian.net", "read:jira-work"));

    var credentials = store.credentialsOf(config);

    assertThat(credentials.authorizationHeader()).isEqualTo("Bearer access-1");
    assertThat(credentials.baseUrl(config.getBaseUrl())).isEqualTo("https://api.atlassian.com/ex/jira/cloud-1");
    assertThat(credentials.writeGranted()).isFalse();
  }

  @Test
  void the_write_scope_lets_the_replication_write_worklogs() {
    config.setOauthConnection(connection("https://example.atlassian.net", "read:jira-work", "write:jira-work"));

    assertThat(store.credentialsOf(config).writeGranted()).isTrue();
  }

  @Test
  void a_base_url_changed_after_connecting_stops_the_replication() {
    config.setOauthConnection(connection("https://other.atlassian.net", "read:jira-work"));

    assertRejected(() -> store.credentialsOf(config), ErrorCode.JI_REPLICATION_OAUTH_SITE_CHANGED);
  }

  @Test
  void a_replication_that_was_never_connected_says_so() {
    config.setSecretId(null);

    assertRejected(() -> store.credentialsOf(config), ErrorCode.JI_REPLICATION_OAUTH_NOT_CONNECTED);
  }

  @Test
  void an_expired_connection_fails_with_the_words_of_the_secret_module() {
    config.setOauthConnection(connection("https://example.atlassian.net", "read:jira-work"));
    when(oauthService.accessToken(500L)).thenThrow(new BusinessRuleException(ErrorCode.JI_REPLICATION_OAUTH_REAUTH_REQUIRED));

    assertRejected(() -> store.credentialsOf(config), ErrorCode.JI_REPLICATION_OAUTH_REAUTH_REQUIRED);
  }

  private static JiraOAuthConnection connection(String siteUrl, String... scopes) {
    return new JiraOAuthConnection("account-1", "Person A", "cloud-1", siteUrl, Set.of(scopes), "mgr",
        LocalDateTime.of(2026, 6, 1, 9, 0));
  }

  private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(ex -> assertThat(((ErrorCodeException) ex).getMessages())
            .anySatisfy(message -> assertThat(message.getErrorCode()).isEqualTo(code)));
  }
}
