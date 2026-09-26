package org.tb.dailyreport.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.tb.common.exception.ErrorCode.RL_NOTHING_TO_RELEASE;
import static org.tb.common.exception.ErrorCode.WD_BREAK_TOO_SHORT_6;
import static org.tb.common.exception.ErrorCode.WD_NO_TIMEREPORT;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.domain.OvertimeBalance;
import org.tb.dailyreport.domain.ReviewPeriod;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.TimereportReview;
import org.tb.dailyreport.domain.TimereportReview.DayEntry;
import org.tb.dailyreport.domain.TimereportReview.DayFinding;
import org.tb.dailyreport.domain.TimereportReview.MonthGroup;
import org.tb.dailyreport.domain.TimereportReview.OrderGroup;
import org.tb.dailyreport.viewhelper.TimereportReviewViewHelper.FindingDay;

/**
 * Die Aufbereitung der Übersicht vor der Freigabe (#760): sie formatiert und reicht durch, was der
 * Service entschieden hat, und entscheidet selbst nichts.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportReviewViewHelperTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 8, 3);
  private static final LocalDate TUESDAY = MONDAY.plusDays(1);
  private static final ReviewPeriod PERIOD = new ReviewPeriod(MONDAY, TUESDAY);

  private final ErrorCodeViewHelper errors = new ErrorCodeViewHelper(germanMessages());

  @Test
  void the_difference_carries_its_sign_and_zero_carries_none() {
    assertThat(helper(balance(Duration.ZERO, Duration.ofMinutes(210))).differenceText()).isEqualTo("+3:30");
    assertThat(helper(balance(Duration.ZERO, Duration.ofHours(-12))).differenceText()).isEqualTo("-12:00");
    assertThat(helper(balance(Duration.ZERO, Duration.ZERO)).differenceText()).isEqualTo("0:00");
  }

  @Test
  void the_balance_is_formatted_as_the_overtime_account_computed_it() {
    var review = helper(new OvertimeBalance(Duration.ofHours(16), Duration.ofHours(15), Duration.ofHours(4),
        Duration.ofHours(3)));

    assertThat(review.hasBalance()).isTrue();
    assertThat(review.targetText()).isEqualTo("16:00");
    assertThat(review.bookedText()).isEqualTo("15:00");
    assertThat(review.hasAdjustment()).isTrue();
    assertThat(review.adjustmentText()).isEqualTo("+4:00");
    assertThat(review.differenceText()).isEqualTo("+3:00");
  }

  @Test
  void the_adjustment_is_only_shown_when_there_is_one() {
    assertThat(helper(balance(Duration.ZERO, Duration.ofHours(-1))).hasAdjustment()).isFalse();
  }

  @Test
  void without_an_overtime_account_only_the_booked_time_is_shown() {
    var review = TimereportReviewViewHelper.from(review(null, false, List.of(), List.of(), months(
        day(MONDAY, Duration.ofHours(6), false, List.of()),
        day(TUESDAY, Duration.ofMinutes(90), false, List.of()))), errors);

    assertThat(review.hasBalance()).isFalse();
    assertThat(review.overtimeAccount()).isFalse();
    assertThat(review.detailed()).isTrue();
    assertThat(review.bookedText()).isEqualTo("7:30");
  }

  @Test
  void the_findings_of_a_day_read_their_date_as_it_is_written_here() {
    var monday = new DayFinding(MONDAY, ServiceFeedbackMessage.error(WD_NO_TIMEREPORT, MONDAY));
    var review = TimereportReviewViewHelper.from(review(balance(Duration.ZERO, Duration.ZERO), true,
        List.of(), List.of(monday), months(day(MONDAY, Duration.ZERO, true, List.of(monday.message())))), errors);

    assertThat(review.findingDays()).extracting(FindingDay::messages)
        .containsExactly(List.of("03.08.2026: Es fehlen Buchungen für den Arbeitstag."));
    assertThat(review.months().getFirst().days().getFirst().messages())
        .containsExactly("03.08.2026: Es fehlen Buchungen für den Arbeitstag.");
  }

  @Test
  void the_findings_are_summarised_per_day_with_the_anchor_of_that_day() {
    var noBooking = new DayFinding(MONDAY, ServiceFeedbackMessage.error(WD_NO_TIMEREPORT, MONDAY));
    var noBreak = new DayFinding(TUESDAY, ServiceFeedbackMessage.error(WD_BREAK_TOO_SHORT_6, TUESDAY));
    var noBookingEither = new DayFinding(TUESDAY, ServiceFeedbackMessage.error(WD_NO_TIMEREPORT, TUESDAY));
    var review = TimereportReviewViewHelper.from(review(balance(Duration.ZERO, Duration.ZERO), true, List.of(),
        List.of(noBooking, noBreak, noBookingEither), months()), errors);

    assertThat(review.findingCount()).isEqualTo(3);
    assertThat(review.findingDays()).extracting(FindingDay::date, FindingDay::anchor)
        .containsExactly(
            tuple(MONDAY, "day-2026-08-03"),
            tuple(TUESDAY, "day-2026-08-04"));
    assertThat(review.findingDays().get(1).messages()).hasSize(2);
    assertThat(review.blockedTarget()).isEqualTo(TimereportReviewViewHelper.FINDINGS_ANCHOR);
  }

  /** Wie die Fehler-Toasts: fünf Tage stehen offen, der Rest ist aufklappbar. */
  @Test
  void the_summary_shows_five_days_and_folds_the_rest() {
    var findings = MONDAY.datesUntil(MONDAY.plusDays(7))
        .map(date -> new DayFinding(date, ServiceFeedbackMessage.error(WD_NO_TIMEREPORT, date)))
        .toList();
    var review = TimereportReviewViewHelper.from(review(balance(Duration.ZERO, Duration.ZERO), true, List.of(),
        findings, months()), errors);

    assertThat(review.firstFindingDays()).extracting(FindingDay::date)
        .containsExactly(MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2), MONDAY.plusDays(3), MONDAY.plusDays(4));
    assertThat(review.moreFindingDays()).extracting(FindingDay::date)
        .containsExactly(MONDAY.plusDays(5), MONDAY.plusDays(6));
  }

  @Test
  void a_short_summary_folds_nothing() {
    var monday = new DayFinding(MONDAY, ServiceFeedbackMessage.error(WD_NO_TIMEREPORT, MONDAY));
    var review = TimereportReviewViewHelper.from(review(balance(Duration.ZERO, Duration.ZERO), true, List.of(),
        List.of(monday), months()), errors);

    assertThat(review.firstFindingDays()).hasSize(1);
    assertThat(review.moreFindingDays()).isEmpty();
  }

  @Test
  void a_period_wide_finding_is_resolved_and_blocks_without_details() {
    var review = TimereportReviewViewHelper.from(review(null, true,
        List.of(ServiceFeedbackMessage.error(RL_NOTHING_TO_RELEASE)), List.of(), List.of()), errors);

    assertThat(review.detailed()).isFalse();
    assertThat(review.periodErrors()).containsExactly("Bis zum gewählten Monat ist bereits alles freigegeben.");
    assertThat(review.blockedTarget()).isEqualTo(TimereportReviewViewHelper.PERIOD_ERRORS_ANCHOR);
  }

  /** Ob ein Tag ohne Buchung ist, bestimmt der Service über die Regel, nicht die Buchungen des Tages. */
  @Test
  void a_day_without_booking_is_taken_from_the_service_as_it_is() {
    var withCommittedBooking = new DayEntry(MONDAY, List.of(booking(1L, MONDAY)), Duration.ofHours(8),
        Duration.ZERO, false, null, false, true, List.of());
    var review = TimereportReviewViewHelper.from(review(balance(Duration.ZERO, Duration.ZERO), true, List.of(),
        List.of(), List.of(new MonthGroup(YearMonth.from(MONDAY), Duration.ofHours(8), Duration.ZERO,
            List.of(withCommittedBooking)))), errors);

    var day = review.months().getFirst().days().getFirst();
    assertThat(day.withoutBooking()).isTrue();
    assertThat(day.anchor()).isEqualTo("day-2026-08-03");
    assertThat(day.sumText()).isEqualTo("8:00");
    assertThat(day.holiday()).isFalse();
  }

  @Test
  void the_permissions_of_the_service_are_passed_through() {
    var review = TimereportReviewViewHelper.from(new TimereportReview(42L, "Erika Probe", "epr", false,
        null, null, PERIOD, balance(Duration.ZERO, Duration.ZERO), true, Duration.ofHours(2),
        List.of(), List.of(), List.of(group()), months(), List.of(), Set.of(7L), true, false, 1), errors);

    assertThat(review.editableIds()).containsExactly(7L);
    assertThat(review.anyEditable()).isTrue();
    assertThat(review.canCreate()).isTrue();
    assertThat(review.actionAllowed()).isFalse();
    assertThat(review.hasStandby()).isTrue();
    assertThat(review.standbyText()).isEqualTo("2:00");
    assertThat(review.groups()).extracting(TimereportReviewViewHelper.Group::anchor, TimereportReviewViewHelper.Group::sumText)
        .containsExactly(tuple("order-5", "1:30"));
  }

  @Test
  void no_standby_no_standby_line() {
    assertThat(helper(balance(Duration.ZERO, Duration.ZERO)).hasStandby()).isFalse();
  }

  private TimereportReviewViewHelper helper(OvertimeBalance balance) {
    return TimereportReviewViewHelper.from(review(balance, true, List.of(), List.of(), months()), errors);
  }

  private static OvertimeBalance balance(Duration adjustment, Duration diff) {
    return new OvertimeBalance(Duration.ofHours(16), Duration.ofHours(16).plus(diff).minus(adjustment), adjustment, diff);
  }

  private static TimereportReview review(OvertimeBalance balance, boolean overtimeAccount,
      List<ServiceFeedbackMessage> periodFindings, List<DayFinding> dayFindings, List<MonthGroup> months) {
    return new TimereportReview(42L, "Erika Probe", "epr", true, LocalDate.of(2026, 7, 31), null, PERIOD,
        balance, overtimeAccount, Duration.ZERO, periodFindings, dayFindings, List.of(), months, List.of(),
        Set.of(), true, periodFindings.isEmpty() && dayFindings.isEmpty(), 0);
  }

  private static List<MonthGroup> months(DayEntry... days) {
    var list = days.length > 0 ? List.of(days) : List.of(
        day(MONDAY, Duration.ZERO, false, List.of()), day(TUESDAY, Duration.ZERO, false, List.of()));
    var workingTime = list.stream().map(DayEntry::workingTime).reduce(Duration.ZERO, Duration::plus);
    return List.of(new MonthGroup(YearMonth.from(MONDAY), workingTime, Duration.ZERO, list));
  }

  private static DayEntry day(LocalDate date, Duration workingTime, boolean withoutBooking,
      List<ServiceFeedbackMessage> findings) {
    return new DayEntry(date, List.of(), workingTime, Duration.ZERO, false, null, false, withoutBooking, findings);
  }

  private static OrderGroup group() {
    return new OrderGroup(5L, "ALPHA/01", "ALPHA", "Projekt Alpha", "Entwicklung", "alpha", false,
        Duration.ofMinutes(90), List.of(booking(7L, MONDAY)));
  }

  private static TimereportDTO booking(long id, LocalDate date) {
    return TimereportDTO.builder().id(id).referenceday(date).suborderId(5L).duration(Duration.ofMinutes(90))
        .status("commited").build();
  }

  private static MessageSourceAccessor germanMessages() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("org/tb/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    return new MessageSourceAccessor(messageSource, Locale.GERMANY);
  }
}
