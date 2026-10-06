package de.hbt.salat.jira.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestClient;

/**
 * What every client against a JIRA instance needs, whichever endpoint it talks to: the
 * authenticated {@link RestClient}, endpoint URL assembly and log abbreviation. The builder is
 * injected rather than created here so that tests can bind a {@code MockRestServiceServer} to it.
 */
abstract class AbstractJiraRestClient {

  private final RestClient.Builder restClientBuilder;

  protected AbstractJiraRestClient(RestClient.Builder restClientBuilder) {
    this.restClientBuilder = restClientBuilder;
  }

  /**
   * An authenticated client: HTTP Basic, on Cloud with the account e-mail as user and the API token
   * as password, or on Server a Personal Access Token as bearer token (#1385). Whatever this client
   * writes is authored by that account, which is why a worklog never carries the person who booked.
   */
  protected RestClient clientFor(JiraCredentials credentials) {
    return restClientBuilder.clone()
        .defaultHeader(HttpHeaders.AUTHORIZATION, credentials.authorizationHeader())
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  protected static String endpointUrl(String baseUrl, String path) {
    return (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + path;
  }

  protected static String abbreviate(@Nullable String s, int max) {
    if (s == null) return null;
    if (s.length() <= max) return s;
    return s.substring(0, max) + "…";
  }
}
