package org.tb.dailyreport.controller;

import java.time.Duration;

record FavoriteView(Long id, String label, String comment, Duration duration) {

  private static final int CONFIRM_NAME_MAX_LENGTH = 60;

  /**
   * What the delete confirmation names (#835): the comment, because that is what tells two
   * favourites on the same suborder apart, and the order when there is none. A comment is stored
   * as text and may be long or span several lines, while {@code confirm()} renders plain text in a
   * single dialog - so whitespace is folded away and the rest is cut off.
   */
  public String deleteConfirmName() {
    var name = comment != null && !comment.isBlank() ? comment : label;
    if (name == null) {
      return "";
    }
    var singleLine = name.replaceAll("\\s+", " ").trim();
    return singleLine.length() <= CONFIRM_NAME_MAX_LENGTH
        ? singleLine
        : singleLine.substring(0, CONFIRM_NAME_MAX_LENGTH - 1) + "…";
  }

}
