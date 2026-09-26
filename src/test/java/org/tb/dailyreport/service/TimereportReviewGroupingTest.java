package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.dailyreport.domain.ReviewPeriod;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.TimereportReview.DayEntry;
import org.tb.dailyreport.domain.TimereportReview.DayFinding;
import org.tb.dailyreport.domain.TimereportReview.MonthGroup;
import org.tb.dailyreport.domain.TimereportReview.OrderGroup;
import org.tb.order.domain.OrderType;

/**
 * Die Gliederung der Übersicht vor der Freigabe (#760): nach Auftrag mit Summe je Gruppe, nach Tag
 * mit Tagessumme und nach Monaten, sobald der Zeitraum mehrere umfasst.
 *
 * <p>Der Zeitraum 30.07.2026 (Donnerstag) bis 03.08.2026 (Montag) reicht über zwei Monate und ein
 * Wochenende.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportReviewGroupingTest {

  private static final LocalDate THURSDAY = LocalDate.of(2026, 7, 30);
  private static final LocalDate FRIDAY = THURSDAY.plusDays(1);
  private static final LocalDate SATURDAY = THURSDAY.plusDays(2);
  private static final LocalDate SUNDAY = THURSDAY.plusDays(3);
  private static final LocalDate MONDAY = THURSDAY.plusDays(4);
  private static final ReviewPeriod PERIOD = new ReviewPeriod(THURSDAY, MONDAY);

  private static final long PROJECT = 10L;
  private static final long MEETINGS = 11L;
  private static final long OTHER_CUSTOMER = 20L;
  private static final long STANDBY = 30L;
  private static final Map<Long, String> SUBORDER_SIGNS = Map.of(
      PROJECT, "ALPHA/01", MEETINGS, "ALPHA/02", STANDBY, "ALPHA/09", OTHER_CUSTOMER, "BETA/01");

  @Test
  void bookings_are_grouped_by_suborder_in_the_order_of_order_and_suborder_sign() {
    var groups = TimereportReviewGrouping.byOrder(List.of(
        booking(1, FRIDAY, OTHER_CUSTOMER, 1, 2),
        booking(2, FRIDAY, MEETINGS, 1, 1),
        booking(3, THURSDAY, PROJECT, 1, 3),
        booking(4, MONDAY, STANDBY, 1, 4)));

    assertThat(groups).extracting(OrderGroup::customerorderSign, OrderGroup::completeOrderSign)
        .containsExactly(
            tuple("ALPHA", "ALPHA/01"),
            tuple("ALPHA", "ALPHA/02"),
            tuple("ALPHA", "ALPHA/09"),
            tuple("BETA", "BETA/01"));
  }

  @Test
  void a_group_lists_its_bookings_by_date_and_sequence_and_sums_their_duration() {
    var groups = TimereportReviewGrouping.byOrder(List.of(
        booking(1, FRIDAY, PROJECT, 2, 1),
        booking(2, MONDAY, PROJECT, 1, 2),
        booking(3, FRIDAY, PROJECT, 1, 3),
        booking(4, THURSDAY, PROJECT, 1, 4)));

    var group = groups.getFirst();
    assertThat(groups).hasSize(1);
    assertThat(group.timereports()).extracting(TimereportDTO::getId).containsExactly(4L, 3L, 1L, 2L);
    assertThat(group.duration()).isEqualTo(Duration.ofHours(10));
    assertThat(group.standby()).isFalse();
    assertThat(group.suborderDescription()).isEqualTo("ALPHA/01 description");
    assertThat(group.customerShortname()).isEqualTo("alpha");
  }

  @Test
  void a_standby_group_is_marked_and_sums_its_standby() {
    var groups = TimereportReviewGrouping.byOrder(List.of(booking(1, SATURDAY, STANDBY, 1, 12)));

    assertThat(groups.getFirst().standby()).isTrue();
    assertThat(groups.getFirst().duration()).isEqualTo(Duration.ofHours(12));
  }

  @Test
  void the_days_are_exactly_the_days_of_the_period_grouped_by_month() {
    var months = byMonth(List.of(), Set.of(), Map.of(), List.of(), Set.of());

    assertThat(months).extracting(MonthGroup::month).containsExactly(YearMonth.of(2026, 7), YearMonth.of(2026, 8));
    assertThat(months.get(0).days()).extracting(DayEntry::date).containsExactly(THURSDAY, FRIDAY);
    assertThat(months.get(1).days()).extracting(DayEntry::date).containsExactly(SATURDAY, SUNDAY, MONDAY);
  }

  /** Die Prüfung der Ruhezeit lädt den Vortag nach — gezeigt wird er nicht. */
  @Test
  void a_booking_outside_the_period_is_not_shown() {
    var months = byMonth(List.of(booking(1, THURSDAY.minusDays(1), PROJECT, 1, 8)), Set.of(), Map.of(), List.of(), Set.of());

    assertThat(months.getFirst().days().getFirst().date()).isEqualTo(THURSDAY);
    assertThat(months).flatExtracting(MonthGroup::days).flatExtracting(DayEntry::timereports).isEmpty();
  }

  @Test
  void day_and_month_sums_leave_standby_out_of_the_working_time() {
    var months = byMonth(List.of(
            booking(1, FRIDAY, PROJECT, 1, 6),
            booking(2, FRIDAY, STANDBY, 2, 5),
            booking(3, THURSDAY, PROJECT, 1, 2),
            booking(4, MONDAY, PROJECT, 1, 8)),
        Set.of(), Map.of(), List.of(), Set.of());

    var friday = day(months, FRIDAY);
    assertThat(friday.timereports()).extracting(TimereportDTO::getId).containsExactly(1L, 2L);
    assertThat(friday.workingTime()).isEqualTo(Duration.ofHours(6));
    assertThat(friday.standby()).isEqualTo(Duration.ofHours(5));
    assertThat(months.get(0).workingTime()).isEqualTo(Duration.ofHours(8));
    assertThat(months.get(0).standby()).isEqualTo(Duration.ofHours(5));
    assertThat(months.get(1).workingTime()).isEqualTo(Duration.ofHours(8));
    assertThat(months.get(1).standby()).isEqualTo(Duration.ZERO);
  }

  @Test
  void weekend_holiday_not_worked_without_booking_and_findings_land_on_their_day() {
    var finding = ServiceFeedbackMessage.error(ErrorCode.WD_BEGIN_TIME_MISSING, FRIDAY);
    var months = byMonth(List.of(),
        Set.of(THURSDAY),
        Map.of(MONDAY, "Testfeiertag"),
        List.of(new DayFinding(FRIDAY, finding)),
        Set.of(FRIDAY));

    assertThat(months).flatExtracting(MonthGroup::days)
        .extracting(DayEntry::date, DayEntry::weekend, DayEntry::publicHolidayName, DayEntry::notWorked,
            DayEntry::withoutBooking, day -> day.findings().size())
        .containsExactly(
            tuple(THURSDAY, false, null, true, false, 0),
            tuple(FRIDAY, false, null, false, true, 1),
            tuple(SATURDAY, true, null, false, false, 0),
            tuple(SUNDAY, true, null, false, false, 0),
            tuple(MONDAY, false, "Testfeiertag", false, false, 0));
    assertThat(day(months, FRIDAY).findings()).containsExactly(finding);
  }

  /** Ein Tag ohne Buchung ist nur, was die Regel dafür hält — die Gliederung leitet es nicht selbst ab. */
  @Test
  void a_day_without_bookings_is_not_marked_on_its_own() {
    var months = byMonth(List.of(), Set.of(), Map.of(), List.of(), Set.of());

    assertThat(months).flatExtracting(MonthGroup::days).extracting(DayEntry::withoutBooking).containsOnly(false);
  }

  @Test
  void an_empty_period_has_no_month() {
    var empty = new ReviewPeriod(MONDAY, THURSDAY);

    assertThat(TimereportReviewGrouping.byMonth(empty, List.of(), Set.of(), Map.of(), List.of(), Set.of())).isEmpty();
  }

  private static List<MonthGroup> byMonth(List<TimereportDTO> timereports, Set<LocalDate> notWorkedDays,
      Map<LocalDate, String> publicHolidayNames, List<DayFinding> dayFindings, Set<LocalDate> daysWithoutBooking) {
    return TimereportReviewGrouping.byMonth(PERIOD, timereports, notWorkedDays, publicHolidayNames, dayFindings, daysWithoutBooking);
  }

  private static DayEntry day(List<MonthGroup> months, LocalDate date) {
    return months.stream().flatMap(month -> month.days().stream())
        .filter(day -> day.date().equals(date))
        .findFirst().orElseThrow();
  }

  private static TimereportDTO booking(long id, LocalDate date, long suborderId, int sequence, int hours) {
    var completeOrderSign = SUBORDER_SIGNS.get(suborderId);
    var customerorderSign = completeOrderSign.substring(0, completeOrderSign.indexOf('/'));
    return TimereportDTO.builder()
        .id(id)
        .referenceday(date)
        .suborderId(suborderId)
        .completeOrderSign(completeOrderSign)
        .customerorderSign(customerorderSign)
        .customerorderDescription(customerorderSign + " description")
        .suborderDescription(completeOrderSign + " description")
        .customerShortname(customerorderSign.toLowerCase())
        .orderType(suborderId == STANDBY ? OrderType.BEREITSCHAFT : OrderType.STANDARD)
        .sequencenumber(sequence)
        .duration(Duration.ofHours(hours))
        .build();
  }
}
