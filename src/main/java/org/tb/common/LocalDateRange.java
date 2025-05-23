package org.tb.common;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import jakarta.annotation.Nullable;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.tb.common.util.DateUtils;

@Data
@RequiredArgsConstructor
public class LocalDateRange implements Comparable<LocalDateRange> {

  private final static LocalDate FINIT_FROM_BOUNDARY = LocalDate.of(1970, 1, 1);
  private final static LocalDate FINIT_UNTIL_BOUNDARY = LocalDate.of(2999, 12, 31);

  @Nullable
  private final LocalDate from;
  @Nullable
  private final LocalDate until;

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
  
}
