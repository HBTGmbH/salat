package org.tb.common;

import java.time.LocalDate;
import java.time.YearMonth;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.tb.common.util.DateUtils;

@Data
@RequiredArgsConstructor
public class DateRange {

  private final static LocalDate FINIT_FROM_BOUNDARY = LocalDate.of(1970, 1, 1);
  private final static LocalDate FINIT_UNTIL_BOUNDARY = LocalDate.of(2999, 12, 31);

  @Nullable
  private final LocalDate from;
  @Nullable
  private final LocalDate until;

  public DateRange(YearMonth month) {
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

  public boolean contains(DateRange dateRange) {
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

  public boolean overlaps(DateRange dateRange) {
    if (dateRange == null) {
      return false;
    }

    LocalDate from = isInfiniteFrom() ? FINIT_FROM_BOUNDARY : this.from;
    LocalDate until = isInfiniteUntil() ? FINIT_UNTIL_BOUNDARY : this.until;
    LocalDate dateFrom = dateRange.isInfiniteFrom() ? FINIT_FROM_BOUNDARY : dateRange.from;
    LocalDate dateUntil = dateRange.isInfiniteUntil() ? FINIT_UNTIL_BOUNDARY : dateRange.until;

    return !from.isAfter(dateUntil) && !until.isBefore(dateFrom);
  }

  public DateRange intersection(DateRange dateRange) {
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
    return new DateRange(maxFrom, minUntil);
  }

  public boolean isAfter(LocalDate date) {
    return date != null && from != null && from.isAfter(date);
  }

  public boolean isBefore(LocalDate date) {
    return date != null && until != null && date.isAfter(until);
  }

}
