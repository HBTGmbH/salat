package de.hbt.salat.jira.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.jira.oauth.JiraOAuthTokenClient.TokenRequestException;
import de.hbt.salat.jira.oauth.JiraOAuthTokenClient.Tokens;
import de.hbt.salat.secret.domain.Token;
import de.hbt.salat.secret.domain.SecretStatus;
import de.hbt.salat.secret.service.SecretService;

/**
 * OAuth against Atlassian (#1417, ADR-0038 §§5–8): the attempt from start to callback, and the
 * renewal of the tokens under the lock of their secret. Against the database and with the key of the
 * test configuration; the token endpoint of Atlassian is mocked.
 *
 * <p>Not transactional: the renewal reads and writes in transactions of its own, and two callers
 * from two threads only see what was committed. Every test removes the secrets it created.
 */
@SpringBootTest
@FixedClock
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraOAuthServiceTest {

  private static final String OWNER = "jira-replication:7";

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @MockitoBean
  private JiraOAuthTokenClient tokenClient;

  @Autowired
  private JiraOAuthService oauthService;

  @Autowired
  private SecretService secretService;

  @Autowired
  private SalatProperties properties;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private final List<Long> createdSecrets = new ArrayList<>();

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("mgr");
  }

  @AfterEach
  void tearDown() {
    when(authorizedUser.isManager()).thenReturn(true);
    createdSecrets.forEach(secretService::delete);
  }

  @Test
  void the_browser_goes_to_the_provider_with_state_and_an_s256_challenge() {
    var authorization = authorize();

    var query = queryOf(authorization);
    assertThat(authorization.redirectUrl()).startsWith("https://auth.atlassian.com/authorize?");
    assertThat(query.get("client_id")).isEqualTo("test-client");
    assertThat(query.get("response_type")).isEqualTo("code");
    assertThat(query.get("redirect_uri")).isEqualTo("http://localhost:8080/jira/replications/oauth/callback");
    assertThat(query.get("scope")).isEqualTo("read:jira-work offline_access");
    assertThat(query.get("audience")).isEqualTo("api.atlassian.com");
    assertThat(query.get("code_challenge_method")).isEqualTo("S256");
    assertThat(query.get("state")).hasSizeGreaterThanOrEqualTo(43);
    assertThat(query.get("code_challenge")).hasSize(43);
  }

  /** ADR-0038 §8: in the browser, not in the session — and not readable there. */
  @Test
  void the_attempt_travels_in_an_encrypted_host_cookie_for_ten_minutes() {
    var authorization = authorize();

    var cookie = authorization.cookie();
    assertThat(cookie.getName()).isEqualTo("__Host-salat-oauth");
    assertThat(cookie.getPath()).isEqualTo("/");
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Lax");
    assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(10));
    assertThat(cookie.getValue()).doesNotContain(queryOf(authorization).get("state")).doesNotContain(OWNER);
  }

  @Test
  void the_callback_names_the_owner_and_exchanges_the_code_with_the_verifier_of_the_challenge() {
    var authorization = authorize();
    var query = queryOf(authorization);
    when(tokenClient.exchange(any(), eq("the-code"), any())).thenAnswer(invocation -> {
      String verifier = invocation.getArgument(2);
      assertThat(JiraOAuthService.codeChallenge(verifier)).isEqualTo(query.get("code_challenge"));
      return tokens("access-1", "refresh-1");
    });

    assertThat(oauthService.ownerOf(authorization.cookie().getValue(), query.get("state"))).isEqualTo(OWNER);
    var grant = oauthService.complete(authorization.cookie().getValue(), query.get("state"), "the-code", null);

    assertThat(grant.accessToken()).isEqualTo("access-1");
    assertThat(grant.refreshToken()).isEqualTo("refresh-1");
  }

  @Test
  void a_callback_with_another_state_is_rejected() {
    var authorization = authorize();

    assertRejected(() -> oauthService.complete(authorization.cookie().getValue(), "forged", "code", null),
        ErrorCode.JI_REPLICATION_OAUTH_STATE_INVALID);
    verify(tokenClient, never()).exchange(any(), any(), any());
  }

  @Test
  void a_callback_without_the_cookie_is_rejected() {
    var state = queryOf(authorize()).get("state");

    assertRejected(() -> oauthService.ownerOf(null, state), ErrorCode.JI_REPLICATION_OAUTH_STATE_INVALID);
    assertRejected(() -> oauthService.ownerOf("test.bm90LWEtY29va2ll", state),
        ErrorCode.JI_REPLICATION_OAUTH_STATE_INVALID);
  }

  @Test
  void a_callback_after_ten_minutes_is_rejected() {
    var authorization = authorize();
    ClockProvider.setClock(Clock.offset(ClockProvider.getClock(), Duration.ofMinutes(11)));

    assertRejected(() -> oauthService.ownerOf(authorization.cookie().getValue(),
        queryOf(authorization).get("state")), ErrorCode.JI_REPLICATION_OAUTH_STATE_INVALID);
  }

  @Test
  void a_callback_of_another_person_is_rejected() {
    var authorization = authorize();
    when(authorizedUser.getLoginSign()).thenReturn("other");

    assertRejected(() -> oauthService.ownerOf(authorization.cookie().getValue(),
        queryOf(authorization).get("state")), ErrorCode.JI_REPLICATION_OAUTH_STATE_INVALID);
  }

  @Test
  void a_declined_consent_exchanges_nothing() {
    var authorization = authorize();

    assertRejected(() -> oauthService.complete(authorization.cookie().getValue(),
        queryOf(authorization).get("state"), null, "access_denied"), ErrorCode.JI_REPLICATION_OAUTH_DENIED);
    verify(tokenClient, never()).exchange(any(), any(), any());
  }

  /** Without the registration in the environment the form does not offer to connect (ADR-0038 §7). */
  @Test
  void without_a_registration_connecting_is_not_offered() {
    var registration = properties.getJira().getOauth();
    var clientSecret = registration.getClientSecret();
    registration.setClientSecret(null);
    try {
      assertThat(oauthService.isAvailable()).isFalse();
      assertRejected(this::authorize, ErrorCode.JI_REPLICATION_OAUTH_NOT_CONFIGURED);
    } finally {
      registration.setClientSecret(clientSecret);
    }
    assertThat(oauthService.isAvailable()).isTrue();
  }

  /** Only the refresh token is stored; each run gets its own access token (ADR-0038 §5). */
  @Test
  void every_call_renews_and_stores_the_rotated_refresh_token_right_away() {
    var id = storedRefreshToken();
    givenRotatingProvider(Duration.ZERO);

    assertThat(oauthService.accessToken(id)).isEqualTo("access-1");
    assertThat(secretService.read(id)).isEqualTo(new Token("refresh-1"));
    assertThat(oauthService.accessToken(id)).isEqualTo("access-2");
    assertThat(secretService.read(id)).isEqualTo(new Token("refresh-2"));
  }

  @Test
  void a_provider_that_does_not_rotate_leaves_the_refresh_token_as_it_is() {
    var id = storedRefreshToken();
    when(tokenClient.refresh(any(), eq("refresh-0"))).thenReturn(tokens("access-1", null));

    assertThat(oauthService.accessToken(id)).isEqualTo("access-1");
    assertThat(secretService.read(id)).isEqualTo(new Token("refresh-0"));
  }

  /**
   * The test of the acceptance criteria: two callers at the same time never renew with the same
   * refresh token. The second one waits and renews with the one the first has just stored — a rotating
   * refresh token used twice could end the connection (ADR-0038 §5).
   */
  @Test
  void two_callers_at_the_same_time_renew_one_after_the_other() throws Exception {
    var id = storedRefreshToken();
    // the second caller is on its way into the lock while the first one is still at the provider
    givenRotatingProvider(Duration.ofMillis(200));

    var executor = Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(() -> oauthService.accessToken(id));
      var second = executor.submit(() -> oauthService.accessToken(id));

      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("access-1", "access-2");
    } finally {
      executor.shutdownNow();
    }
    var used = ArgumentCaptor.forClass(String.class);
    verify(tokenClient, times(2)).refresh(any(), used.capture());
    assertThat(used.getAllValues()).containsExactly("refresh-0", "refresh-1");
    assertThat(secretService.read(id)).isEqualTo(new Token("refresh-2"));
  }

  @Test
  void invalid_grant_removes_the_refresh_token() {
    var id = storedRefreshToken();
    when(tokenClient.refresh(any(), any())).thenThrow(new TokenRequestException("invalid_grant"));

    assertRejected(() -> oauthService.accessToken(id), ErrorCode.JI_REPLICATION_OAUTH_REAUTH_REQUIRED);

    assertThat(secretService.getSummary(id).status()).isEqualTo(SecretStatus.REAUTH_REQUIRED);
    assertThat(secretService.read(id)).isEqualTo(new Token(null));
    // and Atlassian is not asked again with a token it has refused
    assertRejected(() -> oauthService.accessToken(id), ErrorCode.JI_REPLICATION_OAUTH_REAUTH_REQUIRED);
    verify(tokenClient, times(1)).refresh(any(), any());
  }

  @Test
  void another_refusal_leaves_the_refresh_token_as_it_is() {
    var id = storedRefreshToken();
    when(tokenClient.refresh(any(), any())).thenThrow(new TokenRequestException("request_failed"));

    assertRejected(() -> oauthService.accessToken(id), ErrorCode.JI_REPLICATION_OAUTH_TOKEN_REQUEST_FAILED);

    assertThat(secretService.getSummary(id).status()).isEqualTo(SecretStatus.VALID);
    assertThat(secretService.read(id)).isEqualTo(new Token("refresh-0"));
  }

  @Test
  void only_the_management_gets_an_access_token() {
    var id = storedRefreshToken();
    when(authorizedUser.isManager()).thenReturn(false);

    assertThatThrownBy(() -> oauthService.accessToken(id)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(this::authorize).isInstanceOf(AuthorizationException.class);
  }

  private JiraOAuthAuthorization authorize() {
    return oauthService.authorize(OWNER, List.of("read:jira-work", "offline_access"),
        Map.of("audience", "api.atlassian.com"));
  }

  private long storedRefreshToken() {
    var id = new TransactionTemplate(transactionManager).execute(status -> secretService.create(new Token("refresh-0")));
    createdSecrets.add(id);
    return id;
  }

  /** Atlassian as it rotates: refresh-N gives access-(N+1) and refresh-(N+1). */
  private void givenRotatingProvider(Duration delay) {
    when(tokenClient.refresh(any(), any())).thenAnswer(invocation -> {
      Thread.sleep(delay.toMillis());
      var next = Integer.parseInt(invocation.<String>getArgument(1).substring("refresh-".length())) + 1;
      return tokens("access-" + next, "refresh-" + next);
    });
  }

  private static Tokens tokens(String access, String refresh) {
    return new Tokens(access, refresh, Set.of());
  }

  private static Map<String, String> queryOf(JiraOAuthAuthorization authorization) {
    var params = UriComponentsBuilder.fromUriString(authorization.redirectUrl()).build().getQueryParams();
    var decoded = new java.util.HashMap<String, String>();
    params.forEach((name, values) -> decoded.put(name, URLDecoder.decode(values.getFirst(), StandardCharsets.UTF_8)));
    return decoded;
  }

  private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(ex -> assertThat(((ErrorCodeException) ex).getMessages())
            .anySatisfy(message -> assertThat(message.getErrorCode()).isEqualTo(code)));
  }
}
