package org.tb.common.util;

import static java.time.DayOfWeek.SUNDAY;
import static java.time.temporal.TemporalAdjusters.firstDayOfYear;
import static java.time.temporal.TemporalAdjusters.lastDayOfYear;
import static org.tb.common.GlobalConstants.DEFAULT_LOCALE;
import static org.tb.common.GlobalConstants.MONTH_LONGFORM_APRIL;
import static org.tb.common.GlobalConstants.MONTH_LONGFORM_AUGUST;
import static org.tb.common.GlobalConstants.MONTH_LONGFORM_DECEMBER;
import static org.tb.common.GlobalConstants.MONTH_LONGFORM_FEBRUARY;
import static org.tb.common.GlobalConstants.MONTH_LONGFORM_JANUARY;
import static org.tb.common.GlobalConstants.MONTH_LONGFORM_JULY;
import static org.tb.common.GlobalConstants.MONTH_LONGFORM_JUNE;
import static org.tb.common.GlobalConstants.MONTH_LONGFORM_MARCH;
import static org.tb.common.GlobalConstants.MONTH_LONGFORM_MAY;
import static org.tb.common.GlobalConstants.MONTH_LONGFORM_NOVEMBER;
import static org.tb.common.GlobalConstants.MONTH_LONGFORM_OCTOBER;
import static org.tb.common.GlobalConstants.MONTH_LONGFORM_SEPTEMBER;
import static org.tb.common.GlobalConstants.MONTH_SHORTFORM_APRIL;
import static org.tb.common.GlobalConstants.MONTH_SHORTFORM_AUGUST;
import static org.tb.common.GlobalConstants.MONTH_SHORTFORM_DECEMBER;
import static org.tb.common.GlobalConstants.MONTH_SHORTFORM_FEBRUARY;
import static org.tb.common.GlobalConstants.MONTH_SHORTFORM_JANUARY;
import static org.tb.common.GlobalConstants.MONTH_SHORTFORM_JULY;
import static org.tb.common.GlobalConstants.MONTH_SHORTFORM_JUNE;
import static org.tb.common.GlobalConstants.MONTH_SHORTFORM_MARCH;
import static org.tb.common.GlobalConstants.MONTH_SHORTFORM_MAY;
import static org.tb.common.GlobalConstants.MONTH_SHORTFORM_NOVEMBER;
import static org.tb.common.GlobalConstants.MONTH_SHORTFORM_OCTOBER;
import static org.tb.common.GlobalConstants.MONTH_SHORTFORM_SEPTEMBER;
import static org.tb.common.GlobalConstants.STARTING_YEAR;
import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.getCurrentYear;
import static org.tb.common.util.DateUtils.today;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;
import org.tb.common.GlobalConstants;
import org.tb.common.OptionItem;

@UtilityClass
public class DateTimeUtils {

  private static final String[] MONTH_SHORTFORMS = new String[]{
      MONTH_SHORTFORM_JANUARY, MONTH_SHORTFORM_FEBRUARY, MONTH_SHORTFORM_MARCH,
      MONTH_SHORTFORM_APRIL, MONTH_SHORTFORM_MAY, MONTH_SHORTFORM_JUNE,
      MONTH_SHORTFORM_JULY, MONTH_SHORTFORM_AUGUST, MONTH_SHORTFORM_SEPTEMBER,
      MONTH_SHORTFORM_OCTOBER, MONTH_SHORTFORM_NOVEMBER, MONTH_SHORTFORM_DECEMBER
  };

  private static final String[] MONTH_LONGFORMS = new String[]{
      MONTH_LONGFORM_JANUARY, MONTH_LONGFORM_FEBRUARY, MONTH_LONGFORM_MARCH,
      MONTH_LONGFORM_APRIL, MONTH_LONGFORM_MAY, MONTH_LONGFORM_JUNE,
      MONTH_LONGFORM_JULY, MONTH_LONGFORM_AUGUST, MONTH_LONGFORM_SEPTEMBER,
      MONTH_LONGFORM_OCTOBER, MONTH_LONGFORM_NOVEMBER, MONTH_LONGFORM_DECEMBER
  };

  private static final Map<Year, List<OptionItem>> calendarWeeksCache = new HashMap<>();
  public static final int MAX_TIME_REPORT_HOUR = 24;
  public static final int MIN_TIME_REPORT_HOUR = 0;
  public static final int MIN_TIME_REPORT_MINUTE = 0;
  public static final int MAX_TIME_REPORT_MINUTE = 59;

  /*
   * builds up a list of string with current and previous year
   */
  public static List<OptionItem> getYearsToDisplay() {
    List<OptionItem> theList = new ArrayList<>();

    for (int i = STARTING_YEAR; i <= getCurrentYear() + 1; i++) {
      theList.add(intToOptionitem(i));
    }

    return theList;
  }

  /*
   * builds up a list of string with days to display (01-31)
   */
  public static List<OptionItem> getDaysToDisplay() {
    return IntStream.rangeClosed(1, 31).mapToObj(DateTimeUtils::intToOptionitem).collect(Collectors.toList());
  }

  private static OptionItem intToOptionitem(int i) {
    String value = Integer.toString(i);
    String label = i < 10 ? "0" + i : Integer.toString(i);
    return new OptionItem(value, label);
  }

  /**
   * builds up a list of calendar weeks for a certain year
   */
  public static List<OptionItem> getWeeksToDisplay(String yearString) {
    final Year year;
    if (yearString != null) {
      year = Year.of(Integer.parseInt(yearString));
    } else {
      year = Year.of(today().getYear());
    }

    return Optional.ofNullable(calendarWeeksCache.get(year)).orElseGet(() -> {
      var weekItems = new ArrayList<OptionItem>();
      var beginOfYear = year.atDay(1).with(firstDayOfYear());
      var endOfYear = beginOfYear.with(lastDayOfYear());
      var currentDate = beginOfYear;
      WeekFields weekFields = WeekFields.of(DEFAULT_LOCALE);
      while(!currentDate.isAfter(endOfYear)) {
        int week = currentDate.get(weekFields.weekOfYear());
        LocalDate beginOfWeek = currentDate.with(DayOfWeek.MONDAY);
        LocalDate endOfWeek = currentDate.with(SUNDAY);
        String label = "KW" + week + " (" + format(beginOfWeek) + "-" + format(endOfWeek) + ")";
        weekItems.add(new OptionItem(week, label));
        currentDate = currentDate.plusWeeks(1);
      }
      calendarWeeksCache.put(year, Collections.unmodifiableList(weekItems));
      return weekItems;
    });
  }

  public static List<OptionItem> getSerialDayList() {
    List<OptionItem> days = new ArrayList<>();
    days.add(new OptionItem("0", "--"));
    days.addAll(getOptionItemListOfInts(1, GlobalConstants.MAX_SERIAL_BOOKING_DAYS));
    return days;
  }

  /*
   * builds up a list of string with hour to display (1-5)
   */
  public static List<OptionItem> getBreakHoursOptions() {
    return getOptionItemListOfInts(0, 12);
  }

  /*
   * builds up a list of string with duration hours to display (0-23)
   */
  public static List<OptionItem> getTimeReportHoursOptions() {
    return getOptionItemListOfInts(MIN_TIME_REPORT_HOUR, MAX_TIME_REPORT_HOUR);
  }

  /*
   * builds up a list of string with minutes to display (05-55)
   */
  public static List<OptionItem> getTimeReportMinutesOptions(boolean showAllMinutes) {
    if(showAllMinutes) {
      return getOptionItemListOfInts(MIN_TIME_REPORT_MINUTE, MAX_TIME_REPORT_MINUTE);
    }
    List<OptionItem> result = new ArrayList<>();
    result.add(intToOptionitem(0));
    result.add(intToOptionitem(15));
    result.add(intToOptionitem(30));
    result.add(intToOptionitem(45));
    result.add(intToOptionitem(5));
    result.add(intToOptionitem(10));
    result.add(intToOptionitem(20));
    result.add(intToOptionitem(25));
    result.add(intToOptionitem(35));
    result.add(intToOptionitem(40));
    result.add(intToOptionitem(50));
    result.add(intToOptionitem(55));
    return result;
  }

  /*
   * builds up a list of string with hour to display (0-23)
   */
  public static List<OptionItem> getHoursToDisplay() {
    return getOptionItemListOfInts(0, 23);
  }

  /*
   * builds up a list of string with months to display (Jan-Dec)
   */
  public static List<OptionItem> getMonthsToDisplay() {
    List<OptionItem> theList = new ArrayList<>();
    for (int i = 1; i <= 12; i++) {
      String monthValue = MONTH_SHORTFORMS[i - 1];
      String monthLabel = MONTH_LONGFORMS[i - 1];
      theList.add(new OptionItem(monthValue, monthLabel));
    }

    return theList;
  }

  // month 1 - 12
  public static String getShortstringFromMonthMM(int month) {
    return MONTH_SHORTFORMS[month - 1];
  }

  public static String getMonthMMStringFromShortstring(String st) {
    // returns MM as string from short string (e.g., '01' from 'Jan')
    int index = getMonthMMFromShortstring(st);
      if (index == -1) { // st might already be in its correct form
        return st;
      }
      if (index > 0 && index < 10) {
        return "0" + index;
    } else {
      return Integer.toString(index);
    }
  }

  private static int getMonthMMFromShortstring(String st) {
    // returns MM as int from short string (e.g., '01' from 'Jan')
    for (int i = 0; i < MONTH_SHORTFORMS.length; i++) {
      if (st.equals(MONTH_SHORTFORMS[i])) {
        return i + 1;
      }
    }

    return -1;
  }

  private static List<OptionItem> getOptionItemListOfInts(int min, int max) {
    List<OptionItem> theList = new ArrayList<>();
    for (int i = min; i <= max; i++) {
      theList.add(intToOptionitem(i));
    }
    return theList;
  }

}
