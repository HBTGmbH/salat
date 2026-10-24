package de.hbt.salat.auth.domain;

/**
 * One selectable object of a category (#1074): the id as it is written into a rule, and what to show for it.
 *
 * @param id    exactly the value the owning module compares against in its own authorization check
 * @param label what the editor shows for it
 * @param chip  the short key the editor shows once it is picked (#1266), {@code null} for the label
 * @param group the group the editor lists it under (#1523: the group of a report), {@code null} for none; a
 *              provider either groups all its objects or none
 */
public record AuthorizationObject(String id, String label, String chip, String group) {

  public AuthorizationObject(String id, String label) {
    this(id, label, null, null);
  }

  public AuthorizationObject(String id, String label, String chip) {
    this(id, label, chip, null);
  }

}
