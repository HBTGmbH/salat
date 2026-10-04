package de.hbt.salat.dailyreport.controller;

import java.util.List;

/**
 * The inline edit of a comment held because the comment names ticket keys that are no reference of
 * the booking yet (#1326): the row stays open with the text as typed and offers the keys one by one.
 *
 * @param remaining how many more references the suborder allows, {@link Integer#MAX_VALUE} without a bound
 * @param policyText the setting in words, "höchstens 2"
 */
record InlineTicketSuggestion(long timereportId, String taskdescription, List<String> keys, int remaining,
                              String policyText) {

  public boolean limited() {
    return remaining != Integer.MAX_VALUE;
  }
}
