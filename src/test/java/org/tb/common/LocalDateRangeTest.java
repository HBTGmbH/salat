package org.tb.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

public class LocalDateRangeTest {

  @Test
  public void testIsValid_NullFromAndUntil_ReturnsTrue() {
    LocalDateRange range = new LocalDateRange(null, null);
    assertTrue(range.isValid());
  }

  @Test
  public void testIsValid_NullFrom_ReturnsTrue() {
    LocalDate until = LocalDate.now();
    LocalDateRange range = new LocalDateRange(null, until);
    assertTrue(range.isValid());
  }

  @Test
  public void testIsValid_NullUntil_ReturnsTrue() {
    LocalDate from = LocalDate.now();
    LocalDateRange range = new LocalDateRange(from, null);
    assertTrue(range.isValid());
  }

  @Test
  public void testIsValid_FromBeforeUntil_ReturnsTrue() {
    LocalDate from = LocalDate.now();
    LocalDate until = from.plusDays(1);
    LocalDateRange range = new LocalDateRange(from, until);
    assertTrue(range.isValid());
  }

  @Test
  public void testIsValid_FromEqualsUntil_ReturnsTrue() {
    LocalDate date = LocalDate.now();
    LocalDateRange range = new LocalDateRange(date, date);
    assertTrue(range.isValid());
  }

  @Test
  public void testIsValid_FromAfterUntil_ReturnsFalse() {
    LocalDate from = LocalDate.now();
    LocalDate until = from.minusDays(1);
    LocalDateRange range = new LocalDateRange(from, until);
    assertFalse(range.isValid());
  }

  @Test
  public void testIsConnected_NonOverlappingRanges_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(5), currentDate.plusDays(7));
    assertFalse(range1.isConnected(range2));
  }

  @Test
  public void testIsConnected_FullyConnectedRanges_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(2), currentDate.plusDays(5));
    assertTrue(range1.isConnected(range2));
  }

  @Test
  public void testIsConnected_AdjacentRanges_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(4), currentDate.plusDays(7));
    assertTrue(range1.isConnected(range2));
  }

  @Test
  public void testIsConnected_OneRangeInfinite_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(null, currentDate.plusDays(3));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(2), currentDate.plusDays(5));
    assertTrue(range1.isConnected(range2));
  }

  @Test
  public void testIsConnected_BothRangesInfinite_returnsTrue() {
    LocalDateRange range1 = new LocalDateRange(null, null);
    LocalDateRange range2 = new LocalDateRange(LocalDate.now().minusDays(10), null);
    assertTrue(range1.isConnected(range2));
  }

  @Test
  public void testIsConnected_NullRange_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertFalse(range1.isConnected(null));
  }

  @Test
  public void testMinus_NoOverlap_ReturnsOriginalRange() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(3));
    LocalDateRange other = new LocalDateRange(currentDate.plusDays(4), currentDate.plusDays(7));
    assertEquals(List.of(range), range.minus(other));
  }

  @Test
  public void testMinus_CompleteOverlap_ReturnsEmptyList() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(5));
    LocalDateRange other = new LocalDateRange(currentDate.minusDays(1), currentDate.plusDays(6));
    assertEquals(List.of(), range.minus(other));
  }

  @Test
  public void testMinus_PartialOverlapAtStart_ReturnsRemainingRange() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(5));
    LocalDateRange other = new LocalDateRange(currentDate.minusDays(1), currentDate.plusDays(2));
    assertEquals(List.of(new LocalDateRange(currentDate.plusDays(3), currentDate.plusDays(5))), range.minus(other));
  }

  @Test
  public void testMinus_PartialOverlapAtEnd_ReturnsRemainingRange() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(5));
    LocalDateRange other = new LocalDateRange(currentDate.plusDays(4), currentDate.plusDays(6));
    assertEquals(List.of(new LocalDateRange(currentDate, currentDate.plusDays(3))), range.minus(other));
  }

  @Test
  public void testMinus_RangeInside_ReturnsTwoSplitRanges() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(5));
    LocalDateRange other = new LocalDateRange(currentDate.plusDays(2), currentDate.plusDays(3));
    assertEquals(List.of(
        new LocalDateRange(currentDate, currentDate.plusDays(1)),
        new LocalDateRange(currentDate.plusDays(4), currentDate.plusDays(5))
    ), range.minus(other));
  }

  @Test
  public void testContains_dateIsNull_returnsFalse() {
    LocalDate now = LocalDate.now();
    LocalDateRange range = new LocalDateRange(now, now.plusDays(1));
    assertFalse(range.contains((LocalDate) null));
  }

  @Test
  public void testContains_dateIsEqualToFrom_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertTrue(range.contains(currentDate));
  }

  @Test
  public void testContains_dateIsAfterFrom_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertTrue(range.contains(currentDate.plusDays(1)));
  }

  @Test
  public void testContains_untilIsNullAndDateIsAfterFrom_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, null);
    assertTrue(range.contains(currentDate.plusDays(3)));
  }

  @Test
  public void testContains_dateIsEqualToUntil_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertTrue(range.contains(currentDate.plusDays(3)));
  }

  @Test
  public void testContains_dateIsAfterUntil_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertFalse(range.contains(currentDate.plusDays(4)));
  }

  @Test
  public void testContains_yearMonthConstructor_dateInMonth_returnsTrue() {
    YearMonth currentMonth = YearMonth.now();
    LocalDateRange range = new LocalDateRange(currentMonth);
    assertTrue(range.contains(currentMonth.atDay(15)));
  }

  @Test
  public void testContains_yearMonthConstructor_dateOutsideMonth_returnsFalse() {
    YearMonth currentMonth = YearMonth.now();
    LocalDateRange range = new LocalDateRange(currentMonth);
    assertFalse(range.contains(currentMonth.plusMonths(1).atDay(15)));
  }

  @Test
  public void testOverlaps_nullDateRange_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertFalse(range.overlaps((LocalDateRange)null));
  }

  @Test
  public void testOverlaps_nonIntersectingDateRange_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(4), currentDate.plusDays(7));
    assertFalse(range1.overlaps(range2));
    assertFalse(range2.overlaps(range1));
  }

  @Test
  public void testOverlaps_intersectingDateRange_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(2), currentDate.plusDays(5));
    assertTrue(range1.overlaps(range2));
    assertTrue(range2.overlaps(range1));
  }

  @Test
  public void testOverlaps_dateRangeIsSubset_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(5));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(1), currentDate.plusDays(3));
    assertTrue(range1.overlaps(range2));
    assertTrue(range2.overlaps(range1));
  }

  @Test
  public void testOverlaps_dateRangeIsSuperset_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    LocalDateRange range2 = new LocalDateRange(currentDate.minusDays(1), currentDate.plusDays(4));
    assertTrue(range1.overlaps(range2));
    assertTrue(range2.overlaps(range1));
  }

  @Test
  public void testOverlaps_dateRangeFromEqualsUntil_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(3), currentDate.plusDays(6));
    assertTrue(range1.overlaps(range2));
    assertTrue(range2.overlaps(range1));
  }

  @Test
  public void testOverlaps_dateRangeUntilEqualsFrom_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate.plusDays(3), currentDate.plusDays(6));
    LocalDateRange range2 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertTrue(range1.overlaps(range2));
    assertTrue(range2.overlaps(range1));
  }

  @Test
  public void testOverlaps_dateRangeFromBothInfinite_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(null, currentDate.plusDays(6));
    LocalDateRange range2 = new LocalDateRange(null, currentDate.plusDays(3));
    assertTrue(range1.overlaps(range2));
    assertTrue(range2.overlaps(range1));
  }

  @Test
  public void testOverlaps_dateRangeUntilBothInfinite_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate.plusDays(6), null);
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(3), null);
    assertTrue(range1.overlaps(range2));
    assertTrue(range2.overlaps(range1));
  }

  @Test
  public void testOverlaps_dateRangeFRomOneInfinite_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(null, currentDate.plusDays(6));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(3), currentDate.plusDays(7));
    assertTrue(range1.overlaps(range2));
    assertTrue(range2.overlaps(range1));
  }

  @Test
  public void testOverlaps_dateRangeUntilOneInfinite_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate.plusDays(6), null);
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(3), currentDate.plusDays(7));
    assertTrue(range1.overlaps(range2));
    assertTrue(range2.overlaps(range1));
  }

  @Test
  public void testOverlaps_dateRangeFRomOneInfinite_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(null, currentDate.plusDays(2));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(3), currentDate.plusDays(7));
    assertFalse(range1.overlaps(range2));
    assertFalse(range2.overlaps(range1));
  }

  @Test
  public void testOverlaps_dateRangeUntilOneInfinite_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate.plusDays(8), null);
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(3), currentDate.plusDays(7));
    assertFalse(range1.overlaps(range2));
    assertFalse(range2.overlaps(range1));
  }

  @Test
  public void testOverlaps_intersectingYear_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    var year = Year.now();
    assertTrue(range1.overlaps(year));
  }

  @Test
  public void testOverlaps_intersectingYearOneInfinite_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, null);
    var year = Year.now();
    assertTrue(range1.overlaps(year));
  }

  @Test
  public void testOverlaps_intersectingYearTwoInfinite_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(null, null);
    var year = Year.now();
    assertTrue(range1.overlaps(year));
  }

  @Test
  public void testOverlaps_intersectingYearNull_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate);
    assertFalse(range1.overlaps((Year)null));
  }

  @Test
  public void testOverlaps_disjunctYear_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    var year = Year.now().minusYears(1);
    assertFalse(range1.overlaps(year));
  }

  @Test
  public void testOverlaps_disjunctFutureYear_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    var year = Year.now().plusYears(1);
    assertFalse(range1.overlaps(year));
  }

  @Test
  public void testIntersection_nullDateRange_returnsSelf() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertEquals(range, range.intersection(null));
  }

  @Test
  public void testIntersection_nonIntersectingDateRange_returnsNull() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(4), currentDate.plusDays(7));
    assertNull(range1.intersection(range2));
    assertNull(range2.intersection(range1));
  }

  @Test
  public void testIntersection_intersectingDateRange_returnsIntersection() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(2), currentDate.plusDays(5));
    assertEquals(new LocalDateRange(currentDate.plusDays(2), currentDate.plusDays(3)), range1.intersection(range2));
    assertEquals(new LocalDateRange(currentDate.plusDays(2), currentDate.plusDays(3)), range2.intersection(range1));
  }

  @Test
  public void testIntersection_dateRangeIsSubset_returnsDateRange() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(6));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(1), currentDate.plusDays(3));
    assertEquals(range2, range1.intersection(range2));
    assertEquals(range2, range2.intersection(range1));
  }

  @Test
  public void testIntersection_dateRangeIsSuperset_returnsSelf() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate.plusDays(2), currentDate.plusDays(4));
    LocalDateRange range2 = new LocalDateRange(currentDate, currentDate.plusDays(6));
    assertEquals(range1, range1.intersection(range2));
    assertEquals(range1, range2.intersection(range1));
  }

  @Test
  public void testContains_RangeIsNull_returnsFalse() {
    LocalDateRange range = null;
    LocalDateRange toTest = new LocalDateRange(LocalDate.now(), LocalDate.now().plusDays(2));
    assertFalse(toTest.contains(range));
  }

  @Test
  public void testContains_RangeIsSameAsActual_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(1));
    LocalDateRange toTest = new LocalDateRange(currentDate, currentDate.plusDays(1));
    assertTrue(toTest.contains(range));
  }

  @Test
  public void testContains_FromIsNullAndRangeIsValid_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(1));
    LocalDateRange toTest = new LocalDateRange(null, currentDate.plusDays(1));
    assertTrue(toTest.contains(range));
  }

  @Test
  public void testContains_RangeFromIsNullAndRangeUntilIsValid_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(null, currentDate.plusDays(1));
    LocalDateRange toTest = new LocalDateRange(currentDate, currentDate.plusDays(1));
    assertFalse(toTest.contains(range));
  }

  @Test
  public void testContains_RangeUntilIsNullAndRangeFromIsValid_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, null);
    LocalDateRange toTest = new LocalDateRange(currentDate, currentDate.plusDays(1));
    assertFalse(toTest.contains(range));
  }

  @Test
  public void testContains_FromAndUntilAreNull_RangeIsValid_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(1));
    LocalDateRange toTest = new LocalDateRange(null, null);
    assertTrue(toTest.contains(range));
  }

  @Test
  public void testContains_RangeIsAfter_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate.plusDays(2), currentDate.plusDays(4));
    LocalDateRange toTest = new LocalDateRange(currentDate, currentDate.plusDays(1));
    assertFalse(toTest.contains(range));
  }

  @Test
  public void testContains_RangeIsBefore_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate.minusDays(2), currentDate.minusDays(1));
    LocalDateRange toTest = new LocalDateRange(currentDate, currentDate.plusDays(1));
    assertFalse(toTest.contains(range));
  }

  @Test
  public void testContains_RangeIsWithin_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(1));
    LocalDateRange toTest = new LocalDateRange(currentDate.minusDays(1), currentDate.plusDays(2));
    assertTrue(toTest.contains(range));
  }

  @Test
  public void testContains_FromIsInfinie_RangeIsValid_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(1));
    LocalDateRange toTest = new LocalDateRange(null, currentDate.plusDays(2));
    assertTrue(toTest.contains(range));
  }

  @Test
  public void testContains_UntilIsInfinie_RangeIsValid_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(1));
    LocalDateRange toTest = new LocalDateRange(currentDate.minusDays(1), null);
    assertTrue(toTest.contains(range));
  }

  @Test
  public void testIsAfter_DateIsNull_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertFalse(range.isAfter(null));
  }

  @Test
  public void testIsAfter_DateIsBeforeFrom_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertTrue(range.isAfter(currentDate.minusDays(1)));
  }

  @Test
  public void testIsAfter_DateIsEqualToFrom_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertFalse(range.isAfter(currentDate));
  }

  @Test
  public void testIsAfter_DateIsAfterFrom_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertFalse(range.isAfter(currentDate.plusDays(1)));
  }

  @Test
  public void testIsBefore_DateIsNull_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertFalse(range.isBefore(null));
  }

  @Test
  public void testIsBefore_DateIsAfterUntil_returnsTrue() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertTrue(range.isBefore(currentDate.plusDays(4)));
  }

  @Test
  public void testIsBefore_DateIsEqualToUntil_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertFalse(range.isBefore(currentDate.plusDays(3)));
  }

  @Test
  public void testIsBefore_DateIsBeforeUntil_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, currentDate.plusDays(3));
    assertFalse(range.isBefore(currentDate.plusDays(2)));
  }

  @Test
  public void testIsBefore_DateIsBeforeInfiniteBefore_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(null, currentDate.plusDays(3));
    assertFalse(range.isBefore(currentDate.plusDays(2)));
  }

  @Test
  public void testIsBefore_DateIsBeforeInfiniteUntil_returnsFalse() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range = new LocalDateRange(currentDate, null);
    assertFalse(range.isBefore(currentDate.plusDays(2)));
  }

  @Test
  public void testPlus_OverlappingRanges_ReturnsMergedRange() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(5));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(3), currentDate.plusDays(8));
    assertEquals(new LocalDateRange(currentDate, currentDate.plusDays(8)), range1.plus(range2));
  }

  @Test
  public void testPlus_AdjacentRanges_ReturnsMergedRange() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(4), currentDate.plusDays(7));
    assertEquals(new LocalDateRange(currentDate, currentDate.plusDays(7)), range1.plus(range2));
  }

  @Test
  public void testPlus_DisconnectedRanges_ReturnsNull() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(currentDate, currentDate.plusDays(3));
    LocalDateRange range2 = new LocalDateRange(currentDate.plusDays(5), currentDate.plusDays(8));
    assertNull(range1.plus(range2));
  }

  @Test
  public void testPlus_InfiniteRange_ReturnsMergedRange() {
    LocalDate currentDate = LocalDate.now();
    LocalDateRange range1 = new LocalDateRange(null, currentDate.plusDays(3));
    LocalDateRange range2 = new LocalDateRange(currentDate.minusDays(2), currentDate.plusDays(5));
    assertEquals(new LocalDateRange(null, currentDate.plusDays(5)), range1.plus(range2));
  }

  @Test
  public void testPlus_BothRangesInfinite_ReturnsInfiniteRange() {
    LocalDateRange range1 = new LocalDateRange(null, null);
    LocalDateRange range2 = new LocalDateRange(LocalDate.now().minusDays(10), null);
    assertEquals(new LocalDateRange(null, null), range1.plus(range2));
  }
}
