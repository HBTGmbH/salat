package org.tb.jira.service;

import java.util.Iterator;
import org.tb.jira.domain.JiraApiFlavor;

/**
 * Searches issues in one flavour of the JIRA REST API. Paging is the client's business, not the
 * caller's.
 */
public interface JiraSearchClient {

  JiraApiFlavor flavor();

  /**
   * Pages lazily through the whole result set. The returned iterator is single-pass and performs
   * one HTTP request per page, on demand — so a network failure on a later page surfaces from
   * {@code hasNext()}, not from this call.
   */
  Iterator<JiraIssue> search(JiraSearchRequest request);
}
