package de.hbt.salat.jira.domain;

import java.util.List;

/**
 * What the import shows before anything is saved (#1386): the headings, the first rows, and the
 * reading each heading suggests — for the user to confirm or change.
 *
 * @param rowCount the rows below the heading that carry anything at all
 */
public record JiraTicketImportPreview(List<String> headings, List<List<String>> sampleRows,
    List<JiraImportColumn> suggested, int rowCount) {
}
