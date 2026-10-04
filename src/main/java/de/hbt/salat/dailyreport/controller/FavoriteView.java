package de.hbt.salat.dailyreport.controller;

import java.time.Duration;
import java.util.List;

/**
 * A favourite as the list offers it. {@code ticketReferences} are the references the booking behind
 * it carried (#1029, #1326) — two favourites on the same suborder with the same comment differ only
 * in them, so the list has to show them.
 */
record FavoriteView(Long id, String label, String comment, List<String> ticketReferences,
    Duration duration) {

  /** The references in one line, for the delete confirmation. */
  public String ticketReferencesText() {
    return String.join(", ", ticketReferences);
  }

  private static final int SHORT_COMMENT_MAX_LENGTH = 60;

  /**
   * The comment as the delete confirmation shows it (#835) - it is what tells two favourites on
   * the same suborder apart. A comment is stored as text and may be long or span several lines,
   * while the confirmation is one line in a small modal, so whitespace is folded away and the rest
   * is cut off. Empty when the favourite has no comment; the dialog then stands on the order
   * alone, which it names in either case.
   */
  public String shortComment() {
    if (comment == null || comment.isBlank()) {
      return "";
    }
    var singleLine = comment.replaceAll("\\s+", " ").trim();
    return singleLine.length() <= SHORT_COMMENT_MAX_LENGTH
        ? singleLine
        : singleLine.substring(0, SHORT_COMMENT_MAX_LENGTH - 1) + "…";
  }

}
