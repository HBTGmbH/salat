package de.hbt.salat.tag.domain;

/**
 * A tag as values, for the choice in a form or a filter (#847): no entity leaves the module through its service
 * (ADR-0021).
 */
public record TagOption(long id, String name, TagColor color) {

  public static TagOption from(Tag tag) {
    return new TagOption(tag.getId(), tag.getName(), tag.getColor());
  }
}
