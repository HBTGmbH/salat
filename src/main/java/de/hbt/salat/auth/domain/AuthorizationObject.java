package de.hbt.salat.auth.domain;

/**
 * One selectable object of a category (#1074): the id as it is written into a rule, and what to show for it.
 *
 * @param id    exactly the value the owning module compares against in its own authorization check
 * @param label what the editor shows for it
 * @param chip  the short key the editor shows once it is picked (#1266), {@code null} for the label
 */
public record AuthorizationObject(String id, String label, String chip) {

  public AuthorizationObject(String id, String label) {
    this(id, label, null);
  }

}
