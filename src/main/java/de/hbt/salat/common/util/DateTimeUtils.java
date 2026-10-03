package de.hbt.salat.common.util;

import static java.time.DayOfWeek.SUNDAY;
import static java.time.temporal.TemporalAdjusters.firstDayOfYear;
import static java.time.temporal.TemporalAdjusters.lastDayOfYear;
import static de.hbt.salat.common.GlobalConstants.DEFAULT_LOCALE;
import static de.hbt.salat.common.GlobalConstants.STARTING_YEAR;
import static de.hbt.salat.common.util.DateUtils.format;
import static de.hbt.salat.common.util.DateUtils.getCurrentYear;
import static de.hbt.salat.common.util.DateUtils.today;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.OptionItem;

@UtilityClass
public class DateTimeUtils {

  private static final Map<Year, List<OptionItem>> calendarWeeksCache = new HashMap<>();

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
    return getOptionItemListOfInts(GlobalConstants.MIN_TIME_REPORT_HOUR, GlobalConstants.MAX_TIME_REPORT_HOUR);
  }

  /*
   * builds up a list of string with minutes to display (05-55)
   */
  public static List<OptionItem> getTimeReportMinutesOptions(boolean showAllMinutes) {
    if(showAllMinutes) {
      return getOptionItemListOfInts(GlobalConstants.MIN_TIME_REPORT_MINUTE, GlobalConstants.MAX_TIME_REPORT_MINUTE);
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

  private static List<OptionItem> getOptionItemListOfInts(int min, int max) {
    List<OptionItem> theList = new ArrayList<>();
    for (int i = min; i <= max; i++) {
      theList.add(intToOptionitem(i));
    }
    return theList;
  }

  public static LocalDateTime now() {
      return ClockProvider.now();
  }
}
