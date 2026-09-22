package org.tb.jira.service;

import java.util.List;
import org.springframework.web.client.RestClient;

/**
 * What the two search clients share on top of {@link AbstractJiraRestClient}: reading the field
 * catalogue, which both flavours answer the same way and only host at different paths.
 */
abstract class AbstractJiraSearchClient extends AbstractJiraRestClient implements JiraSearchClient {

  protected AbstractJiraSearchClient(RestClient.Builder restClientBuilder) {
    super(restClientBuilder);
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
}
