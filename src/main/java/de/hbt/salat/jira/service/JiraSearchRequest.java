package de.hbt.salat.jira.service;

import java.util.List;

/**
 * Everything a search needs apart from the paging position — that one belongs to the client, which
 * expresses it differently per {@link de.hbt.salat.jira.domain.JiraApiFlavor}.
 */
public record JiraSearchRequest(
    String baseUrl,
    JiraCredentials credentials,
    String jql,
    List<String> fields,
    int pageSize
) {

}
