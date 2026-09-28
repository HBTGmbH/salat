package de.hbt.salat.common.palette;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLEncoder;
import java.util.StringJoiner;

/**
 * The href of a palette target, with its parameters encoded — an order's sign may contain a slash, a
 * space or an ampersand.
 *
 * <p>A filter parameter a link names overwrites what the list remembers (ADR-0022), and one it leaves
 * out is supplied from there. A target that narrows a list therefore names every filter that could
 * hide its object, an empty value included — {@code fCustomerId=} is "no customer", not "the one
 * remembered". Switches that only widen a list ({@code …ShowInactive}) are set only where the object
 * needs them; setting them otherwise would change the remembered state for nothing.
 */
public final class PaletteLink {

  private final String path;
  private final StringJoiner query = new StringJoiner("&", "?", "").setEmptyValue("");

  private PaletteLink(String path) {
    this.path = path;
  }

  public static PaletteLink to(String path) {
    return new PaletteLink(path);
  }

  /** A parameter; {@code null} is sent as the empty value. */
  public PaletteLink param(String name, Object value) {
    query.add(name + "=" + URLEncoder.encode(value == null ? "" : String.valueOf(value), UTF_8));
    return this;
  }

  public PaletteLink paramIf(boolean condition, String name, Object value) {
    return condition ? param(name, value) : this;
  }

  public String build() {
    return path + query;
  }
}
