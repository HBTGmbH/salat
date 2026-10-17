package de.hbt.salat.common.web;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import de.hbt.salat.common.SalatProperties;

/**
 * The maximum number of rows a list shows (#1092, #1380): the booking list, the ticket list and the order lists offer
 * the same steps, remember the chosen one under a filter key of their own, and say so when the result is cut
 * ({@code fragments/list-limit.html}).
 *
 * <p>The limit belongs into the query, not into Java: the list asks the database for the first rows and for the number
 * of all hits, and never loads what it does not show.
 */
@Component
@RequiredArgsConstructor
public class ListLimits {

  /** {@code 0} is the entry "all of them". */
  public static final List<Integer> STEPS = List.of(50, 100, 500, 1000, 0);
  static final int ALL = 0;
  static final int DEFAULT_STEP = 500;

  private final SalatProperties salatProperties;

  /**
   * The limit a request parameter chooses; a missing or unknown value is the default. "All" means the whole filter, but
   * at most {@code salat.booking-list.all-max-rows} rows on the page (#1153) — beyond that the list says it is cut, like
   * with every other step.
   */
  public ListLimit parse(String value) {
    int step = step(value);
    return new ListLimit(step, step == ALL ? salatProperties.getBookingList().getAllMaxRows() : step);
  }

  private static int step(String value) {
    if (value == null || value.isBlank()) return DEFAULT_STEP;
    try {
      int step = Integer.parseInt(value.trim());
      return STEPS.contains(step) ? step : DEFAULT_STEP;
    } catch (NumberFormatException e) {
      return DEFAULT_STEP;
    }
  }

  /**
   * @param step    the chosen entry of {@link #STEPS}, what the select shows and the filter remembers
   * @param maxRows how many rows the query returns at most
   */
  public record ListLimit(int step, int maxRows) {

    /** What {@code fragments/list-limit :: select} reads. */
    public void addTo(Model model) {
      model.addAttribute("limits", STEPS);
      model.addAttribute("limit", step);
    }
  }
}
