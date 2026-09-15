package org.tb.jira.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestClient;

/**
 * Shared plumbing of the JIRA search clients: the authenticated {@link RestClient}, endpoint URL
 * assembly and log abbreviation. The builder is injected rather than created here so that tests can
 * bind a {@code MockRestServiceServer} to it.
 */
abstract class AbstractJiraSearchClient implements JiraSearchClient {

  private final RestClient.Builder restClientBuilder;

  protected AbstractJiraSearchClient(RestClient.Builder restClientBuilder) {
    this.restClientBuilder = restClientBuilder;
  }

  /**
   * An authenticated client. JIRA expects HTTP Basic; on Cloud that is also how API tokens are
   * passed — the account e-mail as user, the token as password.
   */
  protected RestClient clientFor(String username, String password) {
    return restClientBuilder.clone()
        .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth(username, password))
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  protected static String endpointUrl(String baseUrl, String path) {
    return (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + path;
  }

  /**
   * The field catalogue at the given path (#1013). Both flavours answer a plain array, they only
   * differ in where it lives, so the request itself is shared and each client passes its own path.
   */
  protected List<JiraField> fetchFields(JiraFieldsRequest request, String path) {
    var fields = clientFor(request.username(), request.password())
        .get()
        .uri(endpointUrl(request.baseUrl(), path))
        .retrieve()
        .body(JiraField[].class);
    return fields == null ? List.of() : List.of(fields);
  }

  private static String basicAuth(String username, String password) {
    String token = username + ":" + password;
    String encoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    return "Basic " + encoded;
  }

  protected static String abbreviate(@Nullable String s, int max) {
    if (s == null) return null;
    if (s.length() <= max) return s;
    return s.substring(0, max) + "…";
  }
}
