package de.hbt.salat.tag.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The colour of a tag (#847): the twelve named colours of Tabler. Their badges ({@code badge bg-<colour>-lt}) carry
 * white or dark text that {@code salat.css} has measured at 4.8:1 or more in both colour modes (#1039), so no new
 * colour comes into the application. The semantic colours ({@code primary}, {@code danger} …) are left out: a tag
 * says nothing about success or failure.
 */
public enum TagColor {
  BLUE, AZURE, INDIGO, PURPLE, PINK, RED, ORANGE, YELLOW, LIME, GREEN, TEAL, CYAN;

  /**
   * The colour a new tag gets, fixed by its key rather than drawn at random: the same name gets the same colour on
   * every system, and a test can rely on it. {@link String#hashCode()} is specified, not up to the JVM.
   *
   * <p>{@code salat.js} computes the same rule (Java's {@code hashCode} of the key, modulo the {@link #palette()} it
   * gets from the form), so that a tag typed into a form shows the colour it will get before it is saved. Whoever
   * changes the rule here changes it there.
   */
  public static TagColor of(TagName name) {
    var colours = values();
    return colours[Math.floorMod(name.key().hashCode(), colours.length)];
  }

  /** The colours in the order {@link #of} picks from, comma-separated for a form ({@code data-tag-colors}). */
  public static String palette() {
    return Arrays.stream(values()).map(TagColor::cssName).collect(Collectors.joining(","));
  }

  /** The Tabler name, as in {@code bg-blue}. */
  public String cssName() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** The class of the badge that shows the tag. */
  public String badgeClass() {
    return "bg-" + cssName() + "-lt";
  }
}
