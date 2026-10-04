package de.hbt.salat.order.domain;

import static de.hbt.salat.order.domain.TicketReferenceMode.LIMITED;
import static de.hbt.salat.order.domain.TicketReferenceMode.NONE;
import static de.hbt.salat.order.domain.TicketReferenceMode.UNLIMITED;

import java.io.Serializable;
import java.util.Objects;

/**
 * How many ticket references a booking may carry (#1326): none, at most {@code limit}, or any number.
 * An order sets it, a suborder may override it — see {@link Suborder#getEffectiveTicketReferencePolicy()}.
 *
 * @param limit the upper bound for {@link TicketReferenceMode#LIMITED}, at least 1; {@code null} otherwise
 */
public record TicketReferencePolicy(TicketReferenceMode mode, Integer limit) implements Serializable {

  /** What every order starts with, a new one as well as those from before the setting existed. */
  public static final TicketReferencePolicy DEFAULT = new TicketReferencePolicy(UNLIMITED, null);

  public TicketReferencePolicy {
    Objects.requireNonNull(mode, "mode");
    if (mode == LIMITED && (limit == null || limit < 1)) {
      throw new IllegalArgumentException("a limited policy needs a limit of at least 1, was " + limit);
    }
    if (mode != LIMITED) {
      limit = null;
    }
  }

  /**
   * The policy two form fields describe, {@code null} where they describe none: no mode, or "at most"
   * without a usable number. The caller decides whether that means "inherit" or is an error.
   */
  public static TicketReferencePolicy of(TicketReferenceMode mode, Integer limit) {
    if (mode == null || (mode == LIMITED && (limit == null || limit < 1))) {
      return null;
    }
    return new TicketReferencePolicy(mode, limit);
  }

  public boolean allowsAny() {
    return mode != NONE;
  }

  public boolean isUnlimited() {
    return mode == UNLIMITED;
  }

  /** Whether a booking may carry {@code count} references. */
  public boolean permits(int count) {
    return switch (mode) {
      case NONE -> count == 0;
      case LIMITED -> count <= limit;
      case UNLIMITED -> true;
    };
  }

  /** How many more references fit next to {@code used} ones; {@link Integer#MAX_VALUE} without a bound. */
  public int remaining(int used) {
    return switch (mode) {
      case NONE -> 0;
      case LIMITED -> Math.max(0, limit - used);
      case UNLIMITED -> Integer.MAX_VALUE;
    };
  }
}
