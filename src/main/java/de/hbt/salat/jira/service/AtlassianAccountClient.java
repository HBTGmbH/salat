package de.hbt.salat.jira.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * What a fresh OAuth access token of Atlassian is good for (#1417): the sites it reaches, and the
 * account it acts as. Both are asked once, right after the code was exchanged; the replication then
 * remembers the cloud id of its site and the name of the account with the tokens.
 */
@Component
class AtlassianAccountClient {

  static final String ACCESSIBLE_RESOURCES_URL = "https://api.atlassian.com/oauth/token/accessible-resources";
  static final String ME_URL = "https://api.atlassian.com/me";

  private final RestClient restClient;

  AtlassianAccountClient() {
    this(RestClient.builder());
  }

  /** For tests, which bind a {@code MockRestServiceServer} to the builder they pass in. */
  AtlassianAccountClient(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  /** The sites the token reaches — every one the account has granted the app access to. */
  List<Site> accessibleSites(String accessToken) {
    var sites = restClient.get().uri(ACCESSIBLE_RESOURCES_URL)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(new ParameterizedTypeReference<List<Site>>() {
        });
    return sites == null ? List.of() : sites;
  }

  /** The account the token acts as; needs the scope {@code read:me}. */
  Account me(String accessToken) {
    return restClient.get().uri(ME_URL)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Account.class);
  }

  /**
   * @param id the cloud id, the part of the API address that names the site
   * @param url the address of the site, {@code https://example.atlassian.net}
   * @param scopes what the app may do on this site; one id may stand for several products
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Site(String id, String name, String url, List<String> scopes) {

    boolean isJira() {
      return scopes != null && scopes.stream().anyMatch(scope -> scope.endsWith(":jira-work"));
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Account(@JsonProperty("account_id") String accountId, String name, String nickname) {

    /** The name a person recognises the account by; an account may hide its full name. */
    String displayName() {
      return Stream.of(name, nickname)
          .filter(value -> value != null && !value.isBlank())
          .findFirst()
          .orElse(accountId);
    }
  }
}
