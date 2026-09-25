package org.tb.common;

import jakarta.annotation.Nullable;
import jakarta.persistence.metamodel.SingularAttribute;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;
import org.tb.common.util.DateUtils;

/**
 * Whether a validity range has ended — the one place the rule from #950 is written down in code.
 *
 * <p>An entity with a validity range is <em>inactive</em> when its range lies entirely in the past,
 * that is when its end lies before today. Everything else follows from that single comparison:
 *
 * <ul>
 *   <li>An end <em>on</em> today is still active — the comparison is inclusive.</li>
 *   <li>An open end is never inactive, whether it is stored as {@code null} or as the sentinel
 *       31.12.2999 ({@link LocalDateRange#FINIT_UNTIL_BOUNDARY}). The sentinel needs no case of its
 *       own: no real day lies after it, so it can never be before today.</li>
 *   <li>A start in the future is <em>not</em> inactive but merely not yet active, so the start is
 *       deliberately not part of the comparison. A record entered ahead of time has to stay
 *       visible, or it gets entered a second time.</li>
 * </ul>
 *
 * <p>This is a different question from {@code hide} and from the explicit {@code active} /
 * {@code enabled} flags — see AGENTS.md, „Gültigkeitszeiträume: aktiv und inaktiv“. Keep those
 * predicates separate from this one.
 *
 * <p>Asking "does it apply on day X" is a different question again and does not belong here: that
 * one looks at the start as well (see e.g. {@code Suborder#isValidAt}). Do not use it as an
 * activity filter.
 */
public final class Validity {

  private Validity() {
  }

  /**
   * Whether the range ending on {@code untilDate} lies entirely in the past.
   */
  public static boolean isInactive(@Nullable LocalDate untilDate) {
    return isInactiveOn(untilDate, DateUtils.today());
  }

  /**
   * Whether the range ending on {@code untilDate} had already ended on {@code date}.
   */
  public static boolean isInactiveOn(@Nullable LocalDate untilDate, LocalDate date) {
    return untilDate != null && untilDate.isBefore(date);
  }

  /**
   * The query-side twin of {@link #isInactive(LocalDate)}: everything that is not inactive today.
   * Today is taken when the specification is built, not when the query runs.
   */
  public static <E> Specification<E> notInactive(SingularAttribute<? super E, LocalDate> untilDate) {
    return notInactiveOn(untilDate, DateUtils.today());
  }

  /**
   * The query-side twin of {@link #isInactiveOn(LocalDate, LocalDate)}.
   */
  public static <E> Specification<E> notInactiveOn(SingularAttribute<? super E, LocalDate> untilDate,
      LocalDate date) {
    return (root, query, builder) -> builder.or(
        builder.isNull(root.get(untilDate)),
        builder.greaterThanOrEqualTo(root.get(untilDate), date)
    );
  }
}
