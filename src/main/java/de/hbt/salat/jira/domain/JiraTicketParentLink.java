package de.hbt.salat.jira.domain;

/** A ticket by its key and its parent's, for walking down the tree of an order (#1386). */
public record JiraTicketParentLink(String key, String parentKey) {
}
