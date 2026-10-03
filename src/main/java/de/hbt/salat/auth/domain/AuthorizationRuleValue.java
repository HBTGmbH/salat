package de.hbt.salat.auth.domain;

/**
 * One grantee or object of a rule as the editor shows it (#1204): the id as it is stored, and the record it names
 * today.
 *
 * @param id         the stored value — an id, the wildcard {@code *}, or a value marked unresolved
 * @param label      what to show for it: the name of the record, or the stored value where no record answers
 * @param chip       the short key to show once it is picked (#1266), {@code null} for the label
 * @param unresolved {@code true} where no record answers to the value — one the move to ids could not assign, or one
 *                   whose record was deleted since. Such a value never grants anything and is marked in the editor.
 */
public record AuthorizationRuleValue(String id, String label, String chip, boolean unresolved) {

  /** What the list shows: the short key where there is one, the stored value where there is no label (the wildcard). */
  public String shown() {
    return chip != null ? chip : label != null ? label : id;
  }

  public static AuthorizationRuleValue of(AuthorizationObject object) {
    return new AuthorizationRuleValue(object.id(), object.label(), object.chip(), false);
  }

}
