package de.hbt.salat.employee.domain;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * How long a person has been with the company (#1501): the sum of their contract periods.
 *
 * <ul>
 *   <li>A gap between two contracts moves the anniversary on by its length.</li>
 *   <li>An entry before Salat — recorded in {@link EmployeeEntry} — moves the start of the first
 *       contract back to it. An entry date after that start changes nothing.</li>
 *   <li>A contract without an end counts on; whether a day already lies in the past is the caller's
 *       question.</li>
 *   <li>Overlapping and adjoining contracts count once.</li>
 * </ul>
 */
public final class Tenure {

  /** A stretch without a gap; {@code until} is the last day, {@code null} without an end. */
  private record Stretch(LocalDate from, LocalDate until) {

    boolean covers(LocalDate day) {
      return !from.isAfter(day) && (until == null || !until.isBefore(day));
    }
  }

  private final List<Stretch> stretches;

  private Tenure(List<Stretch> stretches) {
    this.stretches = stretches;
  }

  /**
   * @param contracts the contracts of one person, in any order
   * @param entryDate the day the person joined before Salat, {@code null} without one
   */
  public static Tenure of(Collection<EmploymentSpan> contracts, LocalDate entryDate) {
    var stretches = new ArrayList<Stretch>();
    contracts.stream().sorted(Comparator.comparing(EmploymentSpan::from)).forEach(contract -> {
      var last = stretches.isEmpty() ? null : stretches.getLast();
      if (last != null && (last.until() == null || !contract.from().isAfter(last.until().plusDays(1)))) {
        stretches.set(stretches.size() - 1, new Stretch(last.from(), laterEnd(last.until(), contract.until())));
      } else {
        stretches.add(new Stretch(contract.from(), contract.until()));
      }
    });
    if (entryDate != null && !stretches.isEmpty() && entryDate.isBefore(stretches.getFirst().from())) {
      stretches.set(0, new Stretch(entryDate, stretches.getFirst().until()));
    }
    return new Tenure(List.copyOf(stretches));
  }

  /**
   * The day the person has been with the company for the given number of years; empty while the
   * contracts end before.
   */
  public Optional<LocalDate> reachedOn(int years) {
    if (stretches.isEmpty()) {
      return Optional.empty();
    }
    var day = stretches.getFirst().from().plusYears(years);
    for (int i = 1; i < stretches.size(); i++) {
      // only the last stretch can be without an end: an open one takes in every later contract
      var previousEnd = stretches.get(i - 1).until();
      if (!previousEnd.isBefore(day)) {
        break;
      }
      day = day.plusDays(DAYS.between(previousEnd, stretches.get(i).from()) - 1);
    }
    var reached = day;
    return stretches.stream().anyMatch(stretch -> stretch.covers(reached)) ? Optional.of(reached) : Optional.empty();
  }

  private static LocalDate laterEnd(LocalDate one, LocalDate other) {
    if (one == null || other == null) {
      return null;
    }
    return one.isAfter(other) ? one : other;
  }
}
