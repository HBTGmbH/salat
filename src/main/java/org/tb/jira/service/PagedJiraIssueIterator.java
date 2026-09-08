package org.tb.jira.service;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Serves issues from a buffered page and fetches the next one once that page is used up. Subclasses
 * contribute nothing but the fetch and their own notion of a paging position.
 */
abstract class PagedJiraIssueIterator implements Iterator<JiraIssue> {

  private Iterator<JiraIssue> currentPage = Collections.emptyIterator();
  private boolean exhausted;

  @Override
  public boolean hasNext() {
    while (!currentPage.hasNext() && !exhausted) {
      var page = fetchNextPage();
      if (page == null || page.isEmpty()) {
        exhausted = true;
        currentPage = Collections.emptyIterator();
      } else {
        currentPage = page.iterator();
      }
    }
    return currentPage.hasNext();
  }

  @Override
  public JiraIssue next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    return currentPage.next();
  }

  /**
   * Fetches the next page and advances the paging position. An empty result ends the iteration;
   * implementations additionally call {@link #noMorePages()} once the API itself signalled the last
   * page, so that no pointless request follows it.
   */
  protected abstract List<JiraIssue> fetchNextPage();

  protected final void noMorePages() {
    exhausted = true;
  }
}
