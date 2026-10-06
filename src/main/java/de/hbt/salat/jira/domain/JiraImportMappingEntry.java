package de.hbt.salat.jira.domain;

/**
 * How one column of an imported file was read (#1386), kept with the import: by its heading, so that
 * the next file of the same shape gets the same reading proposed.
 *
 * @param fieldName for {@link JiraImportTarget#ADDITIONAL} the key in {@code custom_fields}
 * @param inherited for {@link JiraImportTarget#ADDITIONAL} whether the field is resolved along the parent chain
 */
public record JiraImportMappingEntry(String heading, JiraImportTarget target, String fieldName, boolean inherited) {
}
