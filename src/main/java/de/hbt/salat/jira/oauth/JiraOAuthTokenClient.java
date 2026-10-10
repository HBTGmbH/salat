package de.hbt.salat.jira.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import de.hbt.salat.common.SalatProperties.Jira.OAuth;
import de.hbt.salat.common.util.ClockProvider;

/**
 * The token endpoint of Atlassian (#1417): the code against the first tokens, the refresh token
 * against new ones.
 *
 * <p>Not the token response clients of Spring Security, which ADR-0038 §2 had in mind: Atlassian
 * expects the request as JSON with the client credentials in it, and answers a refresh token that is
 * no longer valid with {@code 403} and {@code invalid_grant} — Spring sends a form and reads the
 * error only from a {@code 400}. Both cases are plain HTTP here.
 *
 * <p>Neither a token, nor the code, nor the client secret appears in a log line or a message of this
 * class: of an error response only the {@code error} is read (ADR-0038 §9), never the body as a whole.
 */
@Slf4j
@Component
class JiraOAuthTokenClient {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final RestClient restClient;

  JiraOAuthTokenClient() {
    this(RestClient.builder());
  }

  /** For tests, which bind a {@code MockRestServiceServer} to the builder they pass in. */
  JiraOAuthTokenClient(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  /** The authorization code and the PKCE verifier of the attempt against the first tokens. */
  Tokens exchange(OAuth client, String code, String codeVerifier) {
    return request(client, new TokenRequest("authorization_code", client.getClientId(), client.getClientSecret(),
        code, client.getRedirectUri(), codeVerifier, null));
  }

  /** The refresh token against new tokens; with a rotating provider the old one is used up by this. */
  Tokens refresh(OAuth client, String refreshToken) {
    return request(client, new TokenRequest("refresh_token", client.getClientId(), client.getClientSecret(),
        null, null, null, refreshToken));
  }

  private Tokens request(OAuth client, TokenRequest body) {
    TokenResponse response;
    try {
      response = restClient.post().uri(client.getTokenUri())
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(TokenResponse.class);
    } catch (RestClientResponseException ex) {
      var error = errorOf(ex);
      log.warn("OAuth token request ({}) refused: HTTP {}, error={}", body.grantType(), ex.getStatusCode().value(),
          error);
      throw new TokenRequestException(error);
    } catch (RestClientException ex) {
      log.warn("OAuth token request ({}) failed: {}", body.grantType(), ex.getClass().getSimpleName());
      throw new TokenRequestException("request_failed");
    }
    if (response == null || response.accessToken() == null || response.expiresIn() == null) {
      log.warn("OAuth token response ({}) without access token or expiry", body.grantType());
      throw new TokenRequestException("invalid_response");
    }
    var expiresAt = Instant.now(ClockProvider.getClock()).plusSeconds(response.expiresIn());
    return new Tokens(response.accessToken(), expiresAt, response.refreshToken(), scopesOf(response.scope()));
  }

  /** The {@code error} of an OAuth error response, nothing else of the body. */
  private static String errorOf(RestClientResponseException ex) {
    try {
      var error = JSON.readTree(ex.getResponseBodyAsByteArray()).path("error");
      return error.isTextual() ? error.asText() : "unknown";
    } catch (Exception unreadable) {
      return "unknown";
    }
  }

  private static Set<String> scopesOf(String scope) {
    if (scope == null || scope.isBlank()) {
      return Set.of();
    }
    return new LinkedHashSet<>(Arrays.asList(scope.trim().split(" +")));
  }

  /**
   * @param refreshToken {@code null} when the provider returned none
   * @param scopes empty when the provider did not name them
   */
  record Tokens(String accessToken, Instant expiresAt, String refreshToken, Set<String> scopes) {

    @Override
    public String toString() {
      return "Tokens[expiresAt=" + expiresAt + ", scopes=" + scopes + "]";
    }
  }

  /** The provider refused or could not be asked. The message is the OAuth {@code error} and nothing else. */
  static class TokenRequestException extends RuntimeException {

    private final String error;

    TokenRequestException(String error) {
      super(error);
      this.error = error;
    }

    String error() {
      return error;
    }

    /** The refresh token is used up, expired or revoked: only connecting again helps (ADR-0038 §6). */
    boolean isInvalidGrant() {
      return "invalid_grant".equals(error);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record TokenRequest(
      @JsonProperty("grant_type") String grantType,
      @JsonProperty("client_id") String clientId,
      @JsonProperty("client_secret") String clientSecret,
      String code,
      @JsonProperty("redirect_uri") String redirectUri,
      @JsonProperty("code_verifier") String codeVerifier,
      @JsonProperty("refresh_token") String refreshToken
  ) {

    @Override
    public String toString() {
      return "TokenRequest[grantType=" + grantType + "]";
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") Long expiresIn,
      @JsonProperty("refresh_token") String refreshToken,
      String scope
  ) {

    @Override
    public String toString() {
      return "TokenResponse[expiresIn=" + expiresIn + "]";
    }
  }
}
