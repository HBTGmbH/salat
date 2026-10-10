package de.hbt.salat.secret.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import de.hbt.salat.common.SalatProperties.OAuth.Client;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.secret.service.OAuthTokenClient.TokenRequestException;

/** The token endpoint as Atlassian runs it (#1417): JSON in, JSON out, {@code 403} for a used-up refresh token. */
@FixedClock
@DisplayNameGeneration(ReplaceUnderscores.class)
class OAuthTokenClientTest {

  private static final String TOKEN_URI = "https://auth.example.com/oauth/token";

  private MockRestServiceServer provider;
  private OAuthTokenClient client;

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder();
    provider = MockRestServiceServer.bindTo(builder).build();
    client = new OAuthTokenClient(builder);
  }

  @Test
  void the_code_goes_out_as_json_with_client_credentials_verifier_and_redirect_uri() {
    provider.expect(requestTo(TOKEN_URI))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.grant_type").value("authorization_code"))
        .andExpect(jsonPath("$.client_id").value("client"))
        .andExpect(jsonPath("$.client_secret").value("client-secret"))
        .andExpect(jsonPath("$.code").value("the-code"))
        .andExpect(jsonPath("$.code_verifier").value("the-verifier"))
        .andExpect(jsonPath("$.redirect_uri").value("https://salat.example.com/callback"))
        .andExpect(jsonPath("$.refresh_token").doesNotExist())
        .andRespond(withSuccess("""
            {"access_token": "access-1", "expires_in": 3600, "refresh_token": "refresh-1",
             "scope": "read:jira-work offline_access", "token_type": "Bearer"}""", MediaType.APPLICATION_JSON));

    var tokens = client.exchange(registration(), "the-code", "the-verifier");

    assertThat(tokens.accessToken()).isEqualTo("access-1");
    assertThat(tokens.refreshToken()).isEqualTo("refresh-1");
    assertThat(tokens.expiresAt()).isEqualTo(Instant.now(ClockProvider.getClock()).plusSeconds(3600));
    assertThat(tokens.scopes()).containsExactlyInAnyOrder("read:jira-work", "offline_access");
    provider.verify();
  }

  @Test
  void a_refresh_sends_the_refresh_token_and_no_code() {
    provider.expect(requestTo(TOKEN_URI))
        .andExpect(jsonPath("$.grant_type").value("refresh_token"))
        .andExpect(jsonPath("$.refresh_token").value("refresh-1"))
        .andExpect(jsonPath("$.code").doesNotExist())
        .andExpect(jsonPath("$.code_verifier").doesNotExist())
        .andRespond(withSuccess("""
            {"access_token": "access-2", "expires_in": 3600, "refresh_token": "refresh-2"}""",
            MediaType.APPLICATION_JSON));

    var tokens = client.refresh(registration(), "refresh-1");

    assertThat(tokens.refreshToken()).isEqualTo("refresh-2");
    assertThat(tokens.scopes()).isEmpty();
  }

  /** Atlassian answers a used-up, expired or revoked refresh token with 403, not 400. */
  @Test
  void a_refused_refresh_token_is_an_invalid_grant_and_the_message_is_the_error_alone() {
    provider.expect(requestTo(TOKEN_URI))
        .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("""
            {"error": "invalid_grant", "error_description": "Unknown or invalid refresh token refresh-1."}"""));

    var thrown = catchThrowableOfType(TokenRequestException.class, () -> client.refresh(registration(), "refresh-1"));

    assertThat(thrown.isInvalidGrant()).isTrue();
    assertThat(thrown).hasMessage("invalid_grant");
  }

  @Test
  void another_error_is_not_an_invalid_grant() {
    provider.expect(requestTo(TOKEN_URI))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("""
            {"error": "access_denied"}"""));

    var thrown = catchThrowableOfType(TokenRequestException.class, () -> client.refresh(registration(), "refresh-1"));

    assertThat(thrown.isInvalidGrant()).isFalse();
    assertThat(thrown.error()).isEqualTo("access_denied");
  }

  @Test
  void a_body_that_is_no_oauth_error_says_unknown() {
    provider.expect(requestTo(TOKEN_URI))
        .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>refresh-1</html>"));

    var thrown = catchThrowableOfType(TokenRequestException.class, () -> client.refresh(registration(), "refresh-1"));

    assertThat(thrown).hasMessage("unknown");
  }

  @Test
  void nothing_of_a_token_appears_in_the_string_forms() {
    var tokens = new OAuthTokenClient.Tokens("access-1", Instant.EPOCH, "refresh-1", java.util.Set.of());
    var request = new OAuthTokenClient.TokenRequest("refresh_token", "client", "client-secret", null, null, null,
        "refresh-1");

    assertThat(tokens.toString()).doesNotContain("access-1").doesNotContain("refresh-1");
    assertThat(request.toString()).doesNotContain("client-secret").doesNotContain("refresh-1");
  }

  private static Client registration() {
    var client = new Client();
    client.setClientId("client");
    client.setClientSecret("client-secret");
    client.setRedirectUri("https://salat.example.com/callback");
    client.setAuthorizationUri("https://auth.example.com/authorize");
    client.setTokenUri(TOKEN_URI);
    return client;
  }
}
