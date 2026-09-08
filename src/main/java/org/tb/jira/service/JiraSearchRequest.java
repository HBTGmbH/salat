package org.tb.jira.service;

import java.util.List;

/**
 * Everything a search needs apart from the paging position — that one belongs to the client, which
 * expresses it differently per {@link org.tb.jira.domain.JiraApiFlavor}.
 */
public record JiraSearchRequest(
    String baseUrl,
    String username,
    String password,
    String jql,
    List<String> fields,
    int pageSize
) {

}
