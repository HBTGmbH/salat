package de.hbt.salat.jira.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How a ticket names its parent: by the parent's key, or by its JIRA issue id. JIRA does both — the
 * key in the standard {@code parent} and in an epic link, the id in other parent fields and export
 * columns — and the import (#1386) and the replication (#1392) read them by the same rule.
 *
 * <p>An id says nothing on its own: it is translated into the key of the ticket carrying that JIRA
 * id, which the caller looks up — the import in the file and the scope, the replication among the
 * tickets of its scope once the run has stored them all.
 *
 * @param key the parent's key, {@code null} for a reference by id
 * @param jiraId the parent's JIRA id, {@code null} for a reference by key
 */
record JiraParentReference(String key, Long jiraId) {

  /** The most digits a {@code long} always holds. */
  private static final int MAX_ID_DIGITS = 18;

  /**
   * The reference a value names, if any. A number is an id, an object its {@code key}, otherwise its
   * {@code id}. Text of digits is an id too; other text without a blank in it is a key. Text with a
   * blank is a title rather than a reference — and so is the text form of an object.
   */
  static Optional<JiraParentReference> of(Object value) {
    if (value instanceof Map<?, ?> object) {
      return of(object.get("key")).or(() -> of(object.get("id")));
    }
    if (value instanceof Number number) {
      return byId(number.toString());
    }
    if (!(value instanceof String text) || text.isBlank()) {
      return Optional.empty();
    }
    var trimmed = text.trim();
    if (isDigits(trimmed)) {
      return byId(trimmed);
    }
    if (trimmed.chars().anyMatch(Character::isWhitespace) || trimmed.length() > JiraTicketImportReader.KEY_LENGTH) {
      return Optional.empty();
    }
    return Optional.of(new JiraParentReference(JiraTicketImportReader.keyOf(trimmed), null));
  }

  /**
   * The parent's key: the first reference that names a ticket other than the ticket itself.
   *
   * @param ownKey the key of the ticket whose parent is asked for — a parent that is the ticket
   *     itself is none
   * @param keysByJiraId the keys an id translates into; an id without an entry is skipped
   * @return {@code null} when no reference names a parent
   */
  static String firstParentKey(List<JiraParentReference> references, String ownKey, Map<Long, String> keysByJiraId) {
    for (var reference : references) {
      var parentKey = reference.isByKey() ? reference.key() : keysByJiraId.get(reference.jiraId());
      if (parentKey != null && !parentKey.equals(ownKey)) return parentKey;
    }
    return null;
  }

  boolean isByKey() {
    return key != null;
  }

  /** What {@code parent_key} holds until the id is translated. */
  String asStored() {
    return isByKey() ? key : jiraId.toString();
  }

  private static Optional<JiraParentReference> byId(String digits) {
    if (!isDigits(digits) || digits.length() > MAX_ID_DIGITS) return Optional.empty();
    return Optional.of(new JiraParentReference(null, Long.parseLong(digits)));
  }

  private static boolean isDigits(String value) {
    return !value.isEmpty() && value.chars().allMatch(Character::isDigit);
  }
}
