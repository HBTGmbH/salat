package org.tb.jira.service;

/**
 * Everything the field catalogue of one JIRA instance needs (#1013).
 *
 * <p>Deliberately not the URL of a request: the values come from a stored replication config, never
 * from a form. A caller that could name the target would turn the server into an authenticated HTTP
 * client for any address it can reach.
 */
public record JiraFieldsRequest(String baseUrl, String username, String password) {

}
