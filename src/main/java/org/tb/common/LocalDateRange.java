package org.tb.common;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;
import jakarta.annotation.Nullable;
import lombok.Data;
import org.tb.common.util.DateUtils;

@Data
public class LocalDateRange implements Comparable<LocalDateRange> {

  public final static LocalDate FINIT_FROM_BOUNDARY = LocalDate.of(1970, 1, 1);
  public final static LocalDate FINIT_UNTIL_BOUNDARY = LocalDate.of(2999, 12, 31);

  @Nullable
  private final LocalDate from;
  @Nullable
  private final LocalDate until;

  public LocalDateRange(LocalDate from, LocalDate until) {
    this.from = from == FINIT_FROM_BOUNDARY ? null : from;
    this.until = until == FINIT_UNTIL_BOUNDARY ? null : until;
  }

  public LocalDateRange(YearMonth month) {
    this.from = month.atDay(1);
    this.until = month.atEndOfMonth();
  }

  public boolean isInfiniteFrom() {
    return from == null;
  }

  public boolean isInfiniteUntil() {
    return until == null;
  }

  public boolean isInfinite() {
    return isInfiniteFrom() && isInfiniteUntil();
  }

  public boolean contains(LocalDateRange dateRange) {
    if (dateRange == null) {
      return false;
    }
    if(dateRange.isInfiniteFrom() && !isInfiniteFrom()) return false;
    if(dateRange.isInfiniteUntil() && !isInfiniteUntil()) return false;
    return (isInfiniteFrom() || !dateRange.from.isBefore(from)) &&
           (isInfiniteUntil() || !dateRange.until.isAfter(until));
  }

  public boolean contains(LocalDate date) {
    if (date == null) {
      return false;
    }
    if(isInfinite()) return true;
    if(!isInfiniteFrom() && date.isBefore(from)) return false;
    if(!isInfiniteUntil() && date.isAfter(until)) return false;
    return true;
  }

  public boolean overlaps(LocalDateRange dateRange) {
    if (dateRange == null) {
      return false;
    }

    LocalDate from = isInfiniteFrom() ? FINIT_FROM_BOUNDARY : this.from;
    LocalDate until = isInfiniteUntil() ? FINIT_UNTIL_BOUNDARY : this.until;
    LocalDate dateFrom = dateRange.isInfiniteFrom() ? FINIT_FROM_BOUNDARY : dateRange.from;
    LocalDate dateUntil = dateRange.isInfiniteUntil() ? FINIT_UNTIL_BOUNDARY : dateRange.until;

    return !from.isAfter(dateUntil) && !until.isBefore(dateFrom);
  }

  public LocalDateRange intersection(LocalDateRange dateRange) {
    if (dateRange == null) {
      return this;
    }

    LocalDate from = isInfiniteFrom() ? FINIT_FROM_BOUNDARY : this.from;
    LocalDate until = isInfiniteUntil() ? FINIT_UNTIL_BOUNDARY : this.until;
    LocalDate dateFrom = dateRange.isInfiniteFrom() ? FINIT_FROM_BOUNDARY : dateRange.from;
    LocalDate dateUntil = dateRange.isInfiniteUntil() ? FINIT_UNTIL_BOUNDARY : dateRange.until;

    var maxFrom = DateUtils.max(from, dateFrom);
    var minUntil = DateUtils.min(until, dateUntil);

    if(maxFrom.isAfter(minUntil)) return null;
    return new LocalDateRange(maxFrom, minUntil);
  }

  public boolean isAfter(LocalDate date) {
    return date != null && from != null && from.isAfter(date);
  }

  public boolean isBefore(LocalDate date) {
    return date != null && until != null && date.isAfter(until);
  }

  public boolean overlaps(Year year) {
    if (year == null) {
      return false;
    }
    LocalDate yearStart = year.atDay(1);
    LocalDate yearEnd = year.atDay(1).with(TemporalAdjusters.lastDayOfYear());

    LocalDate from = isInfiniteFrom() ? FINIT_FROM_BOUNDARY : this.from;
    LocalDate until = isInfiniteUntil() ? FINIT_UNTIL_BOUNDARY : this.until;

    return !from.isAfter(yearEnd) && !until.isBefore(yearStart);
  }

  @Override
  public int compareTo(LocalDateRange other) {
    if (other == null) {
      throw new NullPointerException("The other DateRange is null");
    }

    if (this.from == null && other.from != null) {
      return -1;
    } else if (this.from != null && other.from == null) {
      return 1;
    } else if (this.from != null && other.from != null) {
      int fromComparison = this.from.compareTo(other.from);
      if (fromComparison != 0) {
        return fromComparison;
      }
    }

    if (this.until == null && other.until != null) {
      return 1;
    } else if (this.until != null && other.until == null) {
      return -1;
    } else if (this.until != null && other.until != null) {
      return this.until.compareTo(other.until);
    }

    return 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LocalDateRange dateRange = (LocalDateRange) o;
    return Objects.equals(from, dateRange.from) && Objects.equals(until, dateRange.until);
  }

  @Override
  public int hashCode() {
    return Objects.hash(from, until);
  }

  @Override
  public String toString() {
    return (from != null ? from.toString() : "...") + " - " + (until != null ? until.toString() : "...");
  }

  public List<LocalDateRange> minus(LocalDateRange other) {
    if (other == null || !overlaps(other)) {
      return List.of(this);
    }

    LocalDate from = isInfiniteFrom() ? FINIT_FROM_BOUNDARY : this.from;
    LocalDate until = isInfiniteUntil() ? FINIT_UNTIL_BOUNDARY : this.until;
    LocalDate otherFrom = other.isInfiniteFrom() ? FINIT_FROM_BOUNDARY : other.from;
    LocalDate otherUntil = other.isInfiniteUntil() ? FINIT_UNTIL_BOUNDARY : other.until;

    if (!otherFrom.isAfter(from) && !otherUntil.isBefore(until)) {
      return List.of();
    }

    if (otherFrom.isAfter(from) && otherUntil.isBefore(until)) {
      return List.of(
          new LocalDateRange(from, otherFrom.minusDays(1)),
          new LocalDateRange(otherUntil.plusDays(1), until)
      );
    }

    if (!otherFrom.isAfter(from)) {
      return List.of(new LocalDateRange(otherUntil.plusDays(1), until));
    }

    return List.of(new LocalDateRange(from, otherFrom.minusDays(1)));
  }

  public boolean isConnected(LocalDateRange other) {
    if (other == null) {
      return false;
    }

    LocalDate from = isInfiniteFrom() ? FINIT_FROM_BOUNDARY : this.from;
    LocalDate until = isInfiniteUntil() ? FINIT_UNTIL_BOUNDARY : this.until;
    LocalDate otherFrom = other.isInfiniteFrom() ? FINIT_FROM_BOUNDARY : other.from;
    LocalDate otherUntil = other.isInfiniteUntil() ? FINIT_UNTIL_BOUNDARY : other.until;

    return !from.isAfter(otherUntil.plusDays(1)) && !until.plusDays(1).isBefore(otherFrom);
  }

  public LocalDateRange plus(LocalDateRange other) {
    if (other == null) {
      return this;
    }
    if (!isConnected(other)) {
      return null;
    }

    LocalDate from = isInfiniteFrom() || other.isInfiniteFrom()
        ? null
        : DateUtils.min(this.from, other.from);
    LocalDate until = isInfiniteUntil() || other.isInfiniteUntil()
        ? null
        : DateUtils.max(this.until, other.until);

    return new LocalDateRange(from, until);
  }

}
