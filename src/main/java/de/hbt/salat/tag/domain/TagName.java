package de.hbt.salat.tag.domain;

import java.util.Locale;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.ContainsPattern;

/**
 * The name of a tag as it is stored (#847), and the key that keeps it unique.
 *
 * <p>Free tagging produces {@code Festpreis}, {@code festpreis} and {@code Festpreis } side by side; the analysis
 * that justifies tags would split them. A name is therefore trimmed and its runs of white space collapse into one
 * blank, and two names are the same tag when their {@link #key()} — the name in lower case — is the same. The key
 * is a column of its own with a unique constraint; the column is binary like every table here, so the lower case
 * is what makes the comparison ignore case, not the collation.
 *
 * @param name the name as shown, normalized
 */
public record TagName(String name) {

  public TagName {
    if (name.length() > Tag.NAME_MAX_LENGTH) {
      throw new InvalidDataException(ErrorCode.TG_NAME_TOO_LONG, Tag.NAME_MAX_LENGTH);
    }
    // the tables are utf8mb3: a character outside the BMP cannot be stored (#1380)
    if (!ContainsPattern.canOccurInUtf8mb3(name)) {
      throw new InvalidDataException(ErrorCode.TG_NAME_UNSUPPORTED_CHARACTER, name);
    }
  }

  /**
   * @return the normalized name, {@code null} for a name that is empty after trimming — an empty chip is no tag
   * @throws InvalidDataException {@code TG-0001} for a name that is too long, {@code TG-0002} for one that cannot be
   *     stored
   */
  public static TagName of(String raw) {
    if (raw == null) {
      return null;
    }
    var normalized = raw.strip().replaceAll("\\s+", " ");
    return normalized.isEmpty() ? null : new TagName(normalized);
  }

  public String key() {
    return name.toLowerCase(Locale.ROOT);
  }
}
