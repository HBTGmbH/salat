package de.hbt.salat.jira.domain;

/**
 * A replicated ticket as offered to the booking form (#982): the key is what gets stored, the
 * summary is what makes it recognisable.
 */
public record JiraTicketSuggestion(String key, String summary) {}
