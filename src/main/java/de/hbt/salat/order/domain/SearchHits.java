package de.hbt.salat.order.domain;

import java.util.List;

/**
 * The first rows a search found and how many it found in all (#1331) — what a dialog or a list with a limit (#1380)
 * needs that shows a few and says how many more there are.
 *
 * @param rows  the rows shown, at most as many as were asked for
 * @param total every row the search found, those shown included
 */
public record SearchHits<T>(List<T> rows, long total) {

  public SearchHits {
    rows = List.copyOf(rows);
  }

  public static <T> SearchHits<T> none() {
    return new SearchHits<>(List.of(), 0);
  }

  /** Whether the search found more than it shows — what a list with a limit says out loud (#1380). */
  public boolean truncated() {
    return rows.size() < total;
  }
}
