package org.tb.dailyreport.viewhelper;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.util.DurationUtils;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.TimereportReview;
import org.tb.dailyreport.domain.TimereportReview.DayEntry;
import org.tb.dailyreport.domain.TimereportReview.MonthGroup;
import org.tb.dailyreport.domain.TimereportReview.OrderGroup;

/**
 * Die Übersicht vor der Freigabe (#760), aufbereitet für die Seite: Dauern als Text, die Differenz
 * mit Vorzeichen, die Befunde aufgelöst und die Sprungmarken der Tage. Sie formatiert nur. Welche
 * Tage ohne Buchung sind, wer bearbeiten oder anlegen darf und ob die Aktion möglich ist, hat der
 * Service entschieden; sie reicht es durch.
 *
 * <p>Die Texte der Befunde beginnen mit ihrem Datum ({@code {0}: …}). Ein {@link LocalDate} als
 * Argument erschiene dort als ISO-Datum, deshalb wird es vor dem Auflösen als {@code dd.MM.yyyy}
 * geschrieben. Dass das Datum am Tag selbst noch einmal steht, ist hingenommen: ein zweiter Satz
 * Texte ohne Datum liefe den Texten der Toasts davon.
 *
 * @param detailed      die Übersicht zeigt Bilanz und Buchungen: der Zeitraum ist nicht leer, und
 *                      kein Befund über ihn hat die Übersicht angehalten. Der Service liefert dann
 *                      jeden Tag des Zeitraums, andernfalls keinen.
 * @param hasBalance    für den Zeitraum gibt es eine Bilanz, also Soll und Differenz
 * @param bookedText    die gebuchte Arbeitszeit ohne Bereitschaft
 * @param differenceText die Veränderung des Überstundenkontos, mit Vorzeichen
 * @param periodErrors  die Befunde über den ganzen Zeitraum, aufgelöst
 * @param findingDays   die Befunde der Tage, je Tag, nach Datum
 * @param findingCount  die Zahl der Befunde an Tagen
 * @param blockedTarget die Sprungmarke der Befunde, die die Aktion sperren
 */
public record TimereportReviewViewHelper(
    long employeecontractId, String employeeName, String employeeSign, boolean ownContract,
    LocalDate releasedUntil, LocalDate acceptedUntil,
    LocalDate begin, LocalDate end, boolean periodEmpty, boolean detailed,
    boolean hasBalance, String targetText, String bookedText,
    boolean hasAdjustment, String adjustmentText, String differenceText,
    boolean overtimeAccount, boolean hasStandby, String standbyText,
    List<String> periodErrors, List<FindingDay> findingDays, int findingCount,
    List<Group> groups, List<Month> months, List<TimereportDTO> beforePeriod,
    Set<Long> editableIds, boolean anyEditable, boolean canCreate, boolean actionAllowed,
    int timereportCount, String blockedTarget) {

  /** Die Sprungmarke des Kastens mit den Befunden der Tage. */
  public static final String FINDINGS_ANCHOR = "review-findings";
  /** Die Sprungmarke des Kastens mit den Befunden über den Zeitraum. */
  public static final String PERIOD_ERRORS_ANCHOR = "review-period-errors";

  /** So viele Tage mit Befunden stehen in der Zusammenfassung offen, der Rest ist aufklappbar. */
  static final int FINDING_DAYS_SHOWN = 5;

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  /** Die Befunde eines Tages, für die Zusammenfassung über der Übersicht. */
  public record FindingDay(LocalDate date, String anchor, List<String> messages) {}

  /** Die Buchungen eines Unterauftrags; die Summe enthält eine Bereitschaft. */
  public record Group(String anchor, String customerorderSign, String customerorderDescription,
      String customerShortname, String completeOrderSign, String suborderDescription,
      boolean standby, String sumText, List<TimereportDTO> timereports) {}

  /** Die Tage eines Monats, mit der Arbeitszeit des Monats ohne Bereitschaft. */
  public record Month(LocalDate firstDay, String sumText, List<Day> days) {}

  /**
   * Ein Tag des Zeitraums.
   *
   * @param withoutBooking ein Arbeitstag ohne Buchung, so wie der Service ihn bestimmt hat
   * @param sumText        die Arbeitszeit des Tages ohne Bereitschaft
   * @param messages       die Befunde des Tages, aufgelöst
   */
  public record Day(LocalDate date, String anchor, boolean weekend, String holidayName, boolean notWorked,
      boolean withoutBooking, String sumText, List<String> messages, List<TimereportDTO> timereports) {

    public boolean holiday() {
      return holidayName != null;
    }
  }

  public static TimereportReviewViewHelper from(TimereportReview review, ErrorCodeViewHelper errors) {
    var period = review.period();
    var balance = review.balance();
    var detailed = !review.byMonth().isEmpty();
    var booked = balance != null
        ? balance.workingTime()
        : review.byMonth().stream().map(MonthGroup::workingTime).reduce(Duration.ZERO, Duration::plus);
    var adjustment = balance != null ? balance.adjustment() : Duration.ZERO;
    var findingDays = findingDays(review, errors);
    return new TimereportReviewViewHelper(
        review.employeecontractId(), review.employeeName(), review.employeeSign(), review.ownContract(),
        review.releasedUntil(), review.acceptedUntil(),
        period.begin(), period.end(), period.isEmpty(), detailed,
        balance != null, balance != null ? DurationUtils.format(balance.target()) : null,
        DurationUtils.format(booked),
        !adjustment.isZero(), signed(adjustment),
        balance != null ? signed(balance.diff()) : null,
        review.overtimeAccount(), !review.standby().isZero(), DurationUtils.format(review.standby()),
        review.periodFindings().stream().map(message -> resolve(message, errors)).toList(),
        findingDays, review.dayFindings().size(),
        review.byOrder().stream().map(TimereportReviewViewHelper::group).toList(),
        review.byMonth().stream().map(month -> month(month, errors)).toList(),
        review.beforePeriod(),
        review.editableTimereportIds(), !review.editableTimereportIds().isEmpty(),
        review.canCreate(), review.actionAllowed(), review.timereportCount(),
        findingDays.isEmpty() ? PERIOD_ERRORS_ANCHOR : FINDINGS_ANCHOR);
  }

  /** Die Tage mit Befunden, die in der Zusammenfassung offen stehen. */
  public List<FindingDay> firstFindingDays() {
    return findingDays.subList(0, Math.min(FINDING_DAYS_SHOWN, findingDays.size()));
  }

  /** Die übrigen Tage mit Befunden, zum Aufklappen. */
  public List<FindingDay> moreFindingDays() {
    return findingDays.subList(Math.min(FINDING_DAYS_SHOWN, findingDays.size()), findingDays.size());
  }

  /** Die Sprungmarke eines Tages in der Sicht nach Tag. */
  public static String dayAnchor(LocalDate date) {
    return "day-" + date;
  }

  /** Eine Dauer mit Vorzeichen; die Null ohne, denn sie ist keine Veränderung. */
  static String signed(Duration duration) {
    if (duration.isZero() || duration.isNegative()) {
      return DurationUtils.format(duration);
    }
    return "+" + DurationUtils.format(duration);
  }

  /** Der Text eines Befunds, seine Datumsangaben als {@code dd.MM.yyyy}. */
  static String resolve(ServiceFeedbackMessage message, ErrorCodeViewHelper errors) {
    List<Object> arguments = message.getArguments().stream()
        .map(argument -> argument instanceof LocalDate date ? (Object) DATE.format(date) : argument)
        .toList();
    var formatted = new ServiceFeedbackMessage(message.getErrorCode(), message.getSeverity(), arguments);
    return errors.toViewMessage(formatted).resolved();
  }

  private static List<FindingDay> findingDays(TimereportReview review, ErrorCodeViewHelper errors) {
    return review.dayFindings().stream()
        .collect(groupingBy(TimereportReview.DayFinding::date, LinkedHashMap::new,
            mapping(finding -> resolve(finding.message(), errors), toList())))
        .entrySet().stream()
        .map(day -> new FindingDay(day.getKey(), dayAnchor(day.getKey()), List.copyOf(day.getValue())))
        .toList();
  }

  private static Group group(OrderGroup group) {
    return new Group("order-" + group.suborderId(), group.customerorderSign(), group.customerorderDescription(),
        group.customerShortname(), group.completeOrderSign(), group.suborderDescription(), group.standby(),
        DurationUtils.format(group.duration()), group.timereports());
  }

  private static Month month(MonthGroup month, ErrorCodeViewHelper errors) {
    return new Month(month.month().atDay(1), DurationUtils.format(month.workingTime()),
        month.days().stream().map(day -> day(day, errors)).toList());
  }

  private static Day day(DayEntry day, ErrorCodeViewHelper errors) {
    return new Day(day.date(), dayAnchor(day.date()), day.weekend(), day.publicHolidayName(), day.notWorked(),
        day.withoutBooking(), DurationUtils.format(day.workingTime()),
        day.findings().stream().map(message -> resolve(message, errors)).toList(),
        day.timereports());
  }
}
