package org.tb.dailyreport.service;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.ReviewPeriod;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.TimereportReview.DayEntry;
import org.tb.dailyreport.domain.TimereportReview.DayFinding;
import org.tb.dailyreport.domain.TimereportReview.MonthGroup;
import org.tb.dailyreport.domain.TimereportReview.OrderGroup;

/**
 * Gliedert die Buchungen eines Zeitraums für die Übersicht vor der Freigabe (#760) und der Abnahme
 * (#1122) — nach Auftrag und nach Tag. Reine Funktionen ohne Datenbank: was zu einem Tag gehört,
 * haben die Aufrufer schon geladen und geprüft, hier wird nur noch sortiert, gruppiert und
 * summiert.
 *
 * <p>Die Summen eines Tages und eines Monats folgen dem Überstundenkonto: Arbeitszeit ohne
 * Bereitschaft, die Bereitschaft für sich (#463). Die Gruppe eines Unterauftrags summiert dagegen
 * die ganze Dauer ihrer Buchungen — eine Bereitschaft bildet eine Gruppe für sich und trägt ihre
 * eigene Summe.
 */
final class TimereportReviewGrouping {

  private static final Comparator<TimereportDTO> BY_DATE_AND_SEQUENCE =
      comparing(TimereportDTO::getReferenceday).thenComparingInt(TimereportDTO::getSequencenumber);

  private TimereportReviewGrouping() {
  }

  /**
   * Eine Gruppe je Unterauftrag, geordnet nach Auftrag und Unterauftrag — die Reihenfolge der
   * Matrix, so steht derselbe Auftrag jeden Monat an derselben Stelle. Die Buchungen einer Gruppe
   * stehen nach Datum und der Reihenfolge des Tages.
   */
  static List<OrderGroup> byOrder(List<TimereportDTO> timereports) {
    return timereports.stream()
        .collect(groupingBy(TimereportDTO::getSuborderId, LinkedHashMap::new, toList()))
        .values().stream()
        .map(TimereportReviewGrouping::orderGroup)
        .sorted(comparing(OrderGroup::customerorderSign, nullsLast(naturalOrder()))
            .thenComparing(OrderGroup::completeOrderSign, nullsLast(naturalOrder()))
            .thenComparingLong(OrderGroup::suborderId))
        .toList();
  }

  /**
   * Jeder Tag des Zeitraums, auch einer ohne Buchung, nach Monaten gegliedert — und nur diese Tage:
   * was vor dem Zeitraum liegt, etwa der Vortag, den die Prüfung der Ruhezeit nachlädt, gehört
   * nicht dazu. Ein leerer Zeitraum ergibt keinen Monat.
   *
   * @param notWorkedDays      die als nicht gearbeitet markierten Tage
   * @param publicHolidayNames die Feiertage mit ihrem Namen
   * @param dayFindings        die Befunde der Tage im Zeitraum
   * @param daysWithoutBooking die Arbeitstage ohne Buchung, wie die Regel
   *                           {@link org.tb.dailyreport.domain.UnbookedWorkingDays} sie bestimmt hat
   */
  static List<MonthGroup> byMonth(ReviewPeriod period, List<TimereportDTO> timereports,
      Set<LocalDate> notWorkedDays, Map<LocalDate, String> publicHolidayNames,
      List<DayFinding> dayFindings, Set<LocalDate> daysWithoutBooking) {
    if (period.isEmpty()) {
      return List.of();
    }
    var timereportsByDay = timereports.stream()
        .filter(timereport -> period.contains(timereport.getReferenceday()))
        .collect(groupingBy(TimereportDTO::getReferenceday));
    var findingsByDay = dayFindings.stream()
        .collect(groupingBy(DayFinding::date, mapping(DayFinding::message, toList())));

    return period.begin().datesUntil(period.end().plusDays(1))
        .map(day -> {
          var timereportsOfDay = timereportsByDay.getOrDefault(day, List.of()).stream()
              .sorted(BY_DATE_AND_SEQUENCE)
              .toList();
          return new DayEntry(day, timereportsOfDay,
              sum(timereportsOfDay, TimereportDTO::getWorkingTime),
              sum(timereportsOfDay, TimereportReviewGrouping::standbyOf),
              !DateUtils.isWeekday(day),
              publicHolidayNames.get(day),
              notWorkedDays.contains(day),
              daysWithoutBooking.contains(day),
              findingsByDay.getOrDefault(day, List.of()));
        })
        .collect(groupingBy(day -> YearMonth.from(day.date()), LinkedHashMap::new, toList()))
        .entrySet().stream()
        .map(month -> new MonthGroup(month.getKey(),
            sum(month.getValue(), DayEntry::workingTime),
            sum(month.getValue(), DayEntry::standby),
            month.getValue()))
        .toList();
  }

  /** Die Bereitschaft unter den Buchungen, die nicht zur Arbeitszeit zählt (#463). */
  static Duration standbyOf(TimereportDTO timereport) {
    return timereport.isStandby() ? timereport.getDuration() : Duration.ZERO;
  }

  static <T> Duration sum(Collection<T> items, Function<T, Duration> duration) {
    return items.stream().map(duration).reduce(Duration.ZERO, Duration::plus);
  }

  private static OrderGroup orderGroup(List<TimereportDTO> ofSuborder) {
    var timereports = ofSuborder.stream().sorted(BY_DATE_AND_SEQUENCE).toList();
    var first = timereports.getFirst();
    return new OrderGroup(first.getSuborderId(), first.getCompleteOrderSign(), first.getCustomerorderSign(),
        first.getCustomerorderDescription(), first.getSuborderDescription(), first.getCustomerShortname(),
        first.isStandby(), sum(timereports, TimereportDTO::getDuration), timereports);
  }
}
