package de.hbt.salat.secret.service;

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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.secret.domain.OAuthAuthorization;
import de.hbt.salat.secret.domain.OAuthConnection;
import de.hbt.salat.secret.domain.OAuthTokens;
import de.hbt.salat.secret.domain.SecretStatus;
import de.hbt.salat.secret.persistence.SecretRepository;
import de.hbt.salat.secret.service.OAuthTokenClient.TokenRequestException;
import de.hbt.salat.secret.service.OAuthTokenClient.Tokens;

/**
 * Outgoing OAuth (#1417, ADR-0038 §§5–8): the attempt from start to callback, and the renewal of the
 * tokens under the lock of their secret. Against the database and with the key of the test
 * configuration; the token endpoint of the provider is mocked.
 *
 * <p>Not transactional: the renewal reads and writes in transactions of its own, and two callers
 * from two threads only see what was committed. Every test removes the secrets it created.
 */
@SpringBootTest
@FixedClock
@DisplayNameGeneration(ReplaceUnderscores.class)
class OAuthServiceTest {

  private static final String PROVIDER = "atlassian";
  private static final String OWNER = "jira-replication:7";

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @MockitoBean
  private OAuthTokenClient tokenClient;

  @Autowired
  private OAuthService oauthService;

  @Autowired
  private SecretService secretService;

  @Autowired
  private SecretRepository repository;

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
    createdSecrets.forEach(repository::deleteById);
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
      assertThat(OAuthService.codeChallenge(verifier)).isEqualTo(query.get("code_challenge"));
      return tokens("access-1", "refresh-1", Duration.ofHours(1));
    });

    assertThat(oauthService.ownerOf(PROVIDER, authorization.cookie().getValue(), query.get("state"))).isEqualTo(OWNER);
    var grant = oauthService.complete(PROVIDER, authorization.cookie().getValue(), query.get("state"), "the-code", null);

    assertThat(grant.accessToken()).isEqualTo("access-1");
    assertThat(grant.refreshToken()).isEqualTo("refresh-1");
  }

  @Test
  void a_callback_with_another_state_is_rejected() {
    var authorization = authorize();

    assertRejected(() -> oauthService.complete(PROVIDER, authorization.cookie().getValue(), "forged", "code", null),
        ErrorCode.SE_OAUTH_STATE_INVALID);
    verify(tokenClient, never()).exchange(any(), any(), any());
  }

  @Test
  void a_callback_without_the_cookie_is_rejected() {
    var state = queryOf(authorize()).get("state");

    assertRejected(() -> oauthService.ownerOf(PROVIDER, null, state), ErrorCode.SE_OAUTH_STATE_INVALID);
    assertRejected(() -> oauthService.ownerOf(PROVIDER, "test.bm90LWEtY29va2ll", state),
        ErrorCode.SE_OAUTH_STATE_INVALID);
  }

  @Test
  void a_callback_after_ten_minutes_is_rejected() {
    var authorization = authorize();
    ClockProvider.setClock(Clock.offset(ClockProvider.getClock(), Duration.ofMinutes(11)));

    assertRejected(() -> oauthService.ownerOf(PROVIDER, authorization.cookie().getValue(),
        queryOf(authorization).get("state")), ErrorCode.SE_OAUTH_STATE_INVALID);
  }

  @Test
  void a_callback_of_another_person_is_rejected() {
    var authorization = authorize();
    when(authorizedUser.getLoginSign()).thenReturn("other");

    assertRejected(() -> oauthService.ownerOf(PROVIDER, authorization.cookie().getValue(),
        queryOf(authorization).get("state")), ErrorCode.SE_OAUTH_STATE_INVALID);
  }

  @Test
  void a_callback_for_another_provider_is_rejected() {
    var authorization = authorize();

    assertRejected(() -> oauthService.ownerOf("github", authorization.cookie().getValue(),
        queryOf(authorization).get("state")), ErrorCode.SE_OAUTH_STATE_INVALID);
  }

  @Test
  void a_declined_consent_exchanges_nothing() {
    var authorization = authorize();

    assertRejected(() -> oauthService.complete(PROVIDER, authorization.cookie().getValue(),
        queryOf(authorization).get("state"), null, "access_denied"), ErrorCode.SE_OAUTH_DENIED);
    verify(tokenClient, never()).exchange(any(), any(), any());
  }

  @Test
  void a_provider_without_registration_is_not_offered() {
    assertThat(oauthService.isAvailable(PROVIDER)).isTrue();
    assertThat(oauthService.isAvailable("github")).isFalse();
    assertRejected(() -> oauthService.authorize("github", OWNER, List.of("read"), Map.of()),
        ErrorCode.SE_OAUTH_NOT_CONFIGURED);
  }

  @Test
  void a_valid_access_token_is_handed_out_without_asking_the_provider() {
    var id = storedConnection(Duration.ofMinutes(30));

    assertThat(oauthService.currentTokens(id).accessToken()).isEqualTo("access-0");
    verify(tokenClient, never()).refresh(any(), any());
  }

  /** Five minutes are the least a token has to have left (ADR-0038 §5). */
  @Test
  void an_expiring_access_token_is_renewed_and_the_rotated_refresh_token_stored() {
    var id = storedConnection(Duration.ofMinutes(4));
    when(tokenClient.refresh(any(), eq("refresh-0"))).thenReturn(tokens("access-1", "refresh-1", Duration.ofHours(1)));

    var renewed = oauthService.currentTokens(id);

    assertThat(renewed.accessToken()).isEqualTo("access-1");
    var stored = (OAuthTokens) secretService.read(id);
    assertThat(stored.accessToken()).isEqualTo("access-1");
    assertThat(stored.refreshToken()).isEqualTo("refresh-1");
    assertThat(stored.connection().accountName()).isEqualTo("Person A");
  }

  /**
   * The test of the acceptance criteria: two callers at the same time, one call to the token
   * endpoint. A rotating refresh token used twice could end the connection (ADR-0038 §5).
   */
  @Test
  void two_callers_at_the_same_time_renew_once() throws Exception {
    var id = storedConnection(Duration.ofMinutes(1));
    var calls = new AtomicInteger();
    when(tokenClient.refresh(any(), any())).thenAnswer(invocation -> {
      calls.incrementAndGet();
      // the second caller is on its way into the lock while the first one is still at the provider
      Thread.sleep(200);
      return tokens("access-1", "refresh-1", Duration.ofHours(1));
    });

    var executor = Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(() -> oauthService.currentTokens(id));
      var second = executor.submit(() -> oauthService.currentTokens(id));

      assertThat(first.get(10, TimeUnit.SECONDS).accessToken()).isEqualTo("access-1");
      assertThat(second.get(10, TimeUnit.SECONDS).accessToken()).isEqualTo("access-1");
    } finally {
      executor.shutdownNow();
    }
    assertThat(calls).hasValue(1);
    verify(tokenClient, times(1)).refresh(any(), eq("refresh-0"));
  }

  @Test
  void invalid_grant_ends_the_connection_and_keeps_who_it_was() {
    var id = storedConnection(Duration.ofMinutes(1));
    when(tokenClient.refresh(any(), any())).thenThrow(new TokenRequestException("invalid_grant"));

    assertRejected(() -> oauthService.currentTokens(id), ErrorCode.SE_OAUTH_REAUTH_REQUIRED);

    var summary = secretService.getSummary(id);
    assertThat(summary.status()).isEqualTo(SecretStatus.REAUTH_REQUIRED);
    assertThat(summary.connection().accountName()).isEqualTo("Person A");
    var stored = (OAuthTokens) secretService.read(id);
    assertThat(stored.accessToken()).isNull();
    assertThat(stored.refreshToken()).isNull();
    // and the provider is not asked again with a token it has refused
    assertRejected(() -> oauthService.currentTokens(id), ErrorCode.SE_OAUTH_REAUTH_REQUIRED);
    verify(tokenClient, times(1)).refresh(any(), any());
  }

  @Test
  void another_refusal_leaves_the_connection_as_it_is() {
    var id = storedConnection(Duration.ofMinutes(1));
    when(tokenClient.refresh(any(), any())).thenThrow(new TokenRequestException("request_failed"));

    assertRejected(() -> oauthService.currentTokens(id), ErrorCode.SE_OAUTH_TOKEN_REQUEST_FAILED);

    assertThat(secretService.getSummary(id).status()).isEqualTo(SecretStatus.VALID);
    assertThat(((OAuthTokens) secretService.read(id)).refreshToken()).isEqualTo("refresh-0");
  }

  @Test
  void only_the_management_reaches_the_tokens() {
    var id = storedConnection(Duration.ofMinutes(30));
    when(authorizedUser.isManager()).thenReturn(false);

    assertThatThrownBy(() -> oauthService.currentTokens(id)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(this::authorize).isInstanceOf(AuthorizationException.class);
  }

  private OAuthAuthorization authorize() {
    return oauthService.authorize(PROVIDER, OWNER, List.of("read:jira-work", "offline_access"),
        Map.of("audience", "api.atlassian.com"));
  }

  private long storedConnection(Duration validFor) {
    var connection = new OAuthConnection(PROVIDER, "account-1", "Person A", "cloud-1", "https://example.atlassian.net",
        Set.of("read:jira-work", "offline_access"), "mgr", LocalDateTime.of(2026, 6, 1, 9, 0));
    var tokens = new OAuthTokens("access-0", now().plus(validFor), "refresh-0", connection);
    var id = new TransactionTemplate(transactionManager).execute(status -> secretService.create(tokens));
    createdSecrets.add(id);
    return id;
  }

  private static Tokens tokens(String access, String refresh, Duration validFor) {
    return new Tokens(access, now().plus(validFor), refresh, Set.of());
  }

  private static Instant now() {
    return Instant.now(ClockProvider.getClock());
  }

  private static Map<String, String> queryOf(OAuthAuthorization authorization) {
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
