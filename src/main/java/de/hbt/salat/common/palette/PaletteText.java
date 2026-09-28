package de.hbt.salat.common.palette;

import java.util.List;

/**
 * A text of the palette as a message key with its arguments. The providers know no locale; the
 * fragment that renders the hits resolves the key.
 */
public record PaletteText(String key, List<String> args) {

  public PaletteText {
    args = List.copyOf(args);
  }

  public static PaletteText of(String key, String... args) {
    return new PaletteText(key, List.of(args));
  }
}
