package org.tb.common.util;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.format.TextStyle.SHORT;
import static java.time.temporal.TemporalAdjusters.firstDayOfMonth;
import static java.time.temporal.TemporalAdjusters.firstDayOfYear;
import static java.time.temporal.TemporalAdjusters.lastDayOfMonth;
import static java.time.temporal.TemporalAdjusters.lastDayOfYear;
import static java.util.Locale.ENGLISH;
import static org.tb.common.GlobalConstants.DEFAULT_DATE_FORMAT;
import static org.tb.common.GlobalConstants.DEFAULT_LOCALE;
import static org.tb.common.GlobalConstants.DEFAULT_TIMEZONE_ID;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.common.GlobalConstants.STARTING_YEAR;

import java.text.ParseException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.tb.common.GlobalConstants;

@Slf4j
public class DateUtils {

    private static final DateTimeFormatter dateFormatter = DateTimeFormatter
        .ofPattern(DEFAULT_DATE_FORMAT)
        .withZone(ZoneId.of(DEFAULT_TIMEZONE_ID));

    private static final DateTimeFormatter yearFormatter = DateTimeFormatter
        .ofPattern("yyyy")
        .withZone(ZoneId.of(DEFAULT_TIMEZONE_ID));

    private static final DateTimeFormatter monthFormatter = DateTimeFormatter
        .ofPattern("MM")
        .withZone(ZoneId.of(DEFAULT_TIMEZONE_ID));

    private static final DateTimeFormatter dayOfMonthFormatter = DateTimeFormatter
        .ofPattern("dd")
        .withZone(ZoneId.of(DEFAULT_TIMEZONE_ID));

    private static final DateTimeFormatter hourFormatter = DateTimeFormatter
        .ofPattern("HH")
        .withZone(ZoneId.of(DEFAULT_TIMEZONE_ID));

    private static final DateTimeFormatter minuteFormatter = DateTimeFormatter
        .ofPattern("HH")
        .withZone(ZoneId.of(DEFAULT_TIMEZONE_ID));

    private static final String[] monthShortStrings = GlobalConstants.MONTH_SHORTFORMS;
    private static final String[] monthLongStrings = GlobalConstants.MONTH_LONGFORMS;

    private static final Map<Year, List<OptionItem>> mapCalendarWeeks = new HashMap<>();

    /**
     * Month is 0-based.
     */
    public static LocalDate of(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    public static String getCurrentYearString() {
        return Integer.toString(getCurrentYear());
    }

    public static int getCurrentYear() {
        return today().getYear();
    }

    public static int getCurrentMonth() {
        return today().getMonthValue();
    }

    public static String getDoW(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(SHORT, ENGLISH);
    }

    public static String getYearString(LocalDate dt) {
        return String.valueOf(dt.getYear());
    }

    public static String getMonthShortString(LocalDate dt) {
        return dt.getMonth().getDisplayName(SHORT, ENGLISH);
    }

    public static String getDayString(LocalDate dt) {
        return formatDayOfMonth(dt);
    }

    private static int getMonthMMFromShortstring(String st) {
        // returns MM as int from short string (e.g., '01' from 'Jan')
        for (int i = 0; i < monthShortStrings.length; i++) {
            if (st.equals(monthShortStrings[i])) {
                return i + 1;
            }
        }

        return -1;
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

    public static int getYear(String st) {
        // returns yyyy as int from String
        return Integer.parseInt(st);
    }

    /**
     * validates if date string has correct date format 'yyyy-mm-dd'
     */
    public static boolean validateDate(String dateString) {
        try {
            dateFormatter.parse(dateString);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public static boolean isWeekday(LocalDate dt) {
        var dow = dt.getDayOfWeek();
        return dow != SATURDAY && dow != SUNDAY;
    }

    /*
     * builds up a list of string with current and previous year
     */
    public static List<OptionItem> getYearsToDisplay() {
        List<OptionItem> theList = new ArrayList<>();

        for (int i = STARTING_YEAR; i <= getCurrentYear() + 1; i++) {
            String yearString = Integer.toString(i);
            theList.add(new OptionItem(yearString, yearString));
        }

        return theList;
    }

    /*
     * builds up a list of string with current and previous years since startyear of contract
     */
    public static List<OptionItem> getYearsSinceContractStartToDisplay(LocalDate validFrom) {
        List<OptionItem> theList = new ArrayList<>();

        int startyear = getYear(validFrom).getValue();
        for (int i = startyear; i <= getCurrentYear() + 1; i++) {
            String yearString = "" + i;
            theList.add(new OptionItem(yearString, yearString));
        }

        return theList;
    }

    /*
     * builds up a list of string with months to display (Jan-Dec)
     */
    public static List<OptionItem> getMonthsToDisplay() {
        List<OptionItem> theList = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String dayValue = monthShortStrings[i - 1];
            String dayLabel = monthLongStrings[i - 1];
            theList.add(new OptionItem(dayValue, dayLabel));
        }

        return theList;
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

        return Optional.ofNullable(mapCalendarWeeks.get(year)).orElseGet(() -> {
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
            mapCalendarWeeks.put(year, Collections.unmodifiableList(weekItems));
            return weekItems;
        });
    }

    /*
     * builds up a list of string with days to display (01-31)
     */
    public static List<OptionItem> getDaysToDisplay() {
        return IntStream.rangeClosed(1, 31).mapToObj(DateUtils::intToOptionitem).collect(Collectors.toList());
    }

    /*
     * builds up a list of string with hour to display (6-21)
     */
    public static List<OptionItem> getHoursToDisplay() {
        List<OptionItem> theList = new ArrayList<>();
        for (int i = 6; i < 22; i++) {
            theList.add(intToOptionitem(i));
        }
        return theList;
    }

    /*
     * builds up a list of string with hour to display (1-5)
     */
    public static List<OptionItem> getCompleteHoursToDisplay() {
        List<OptionItem> theList = new ArrayList<>();
        for (int i = 0; i <= 5; i++) {
            theList.add(intToOptionitem(i));
        }
        return theList;
    }

    /*
     * builds up a list of string with duration hours to display (0-15)
     */
    public static List<OptionItem> getHoursDurationToDisplay() {
        List<OptionItem> theList = new ArrayList<>();
        for (int i = 0; i <= 24; i++) {
            theList.add(intToOptionitem(i));
        }
        return theList;
    }

    /*
     * builds up a list of string with minutes to display (05-55)
     */
    public static List<OptionItem> getMinutesToDisplay() {
        List<OptionItem> theList = new ArrayList<>();
        for (int i = 0; i < 60; i += 5) {
            theList.add(intToOptionitem(i));
        }
        return theList;
    }

    private static OptionItem intToOptionitem(int i) {
        String value = Integer.toString(i);
        String label = i < 10 ? "0" + i : Integer.toString(i);
        return new OptionItem(value, label);
    }

    /**
     * gets the last day of a given month
     * E.g., month given as string '02', last day is either 28 or 29
     */
    public static LocalDate getBeginOfMonth(LocalDate date) {
        return date.with(firstDayOfMonth());
    }

    /**
     * gets the last day of a given month
     * E.g., month given as string '02', last day is either 28 or 29
     */
    public static LocalDate getEndOfMonth(LocalDate date) {
        return date.with(lastDayOfMonth());
    }

    /**
     * gets the last day of a given month
     * E.g., month given as string '02', last day is either 28 or 29
     */
    public static int getLastDayOfMonth(LocalDate date) {
        return date.getMonthValue();
    }

    /**
     * calculates worktime from begin/end times in a form
     *
     * @return double - decimal hours
     */
    public static double calculateTime(int hrbegin, int minbegin, int hrend, int minend) {
        double worktime;

        int hours = hrend - hrbegin;
        int minutes = minend - minbegin;

        if (minutes < 0) {
            hours -= 1;
            minutes += MINUTES_PER_HOUR;
        }
        worktime = hours * 1. + minutes / 60.;

        return worktime;
    }

    /**
     * Takes a LocalDate and a number of days. Changes the LocalDate by adding (amount is positive) or subtracting (amount is negative)
     * the number of days to it. For example, you have some LocalDate and need the next day: input parameters are (date, 1).
     */
    public static LocalDate addDays(LocalDate originalDate, int amount) {
        return originalDate.plusDays(amount);
    }

    public static LocalDate addMonths(LocalDate originalDate, int amount) {
        return originalDate.plusMonths(amount);
    }

    /**
     * Transforms a {@link LocalDate} into 3 {@link String}s, e.g. "09", "Feb", "2011".
     *
     * @return Returns an array of strings with the day at index 0, month at index 1 and year at index 2.
     */
    public static String[] getDateAsStringArray(LocalDate date) {
        String day = formatDayOfMonth(date);
        String year = formatYear(date);
        String month = formatMonth(date);
        int monthValue = Integer.parseInt(month);
        if (monthValue == GlobalConstants.MONTH_INTVALUE_JANUARY) {
            month = GlobalConstants.MONTH_SHORTFORM_JANUARY;
        } else if (monthValue == GlobalConstants.MONTH_INTVALUE_FEBRURAY) {
            month = GlobalConstants.MONTH_SHORTFORM_FEBRUARY;
        } else if (monthValue == GlobalConstants.MONTH_INTVALUE_MARCH) {
            month = GlobalConstants.MONTH_SHORTFORM_MARCH;
        } else if (monthValue == GlobalConstants.MONTH_INTVALUE_APRIL) {
            month = GlobalConstants.MONTH_SHORTFORM_APRIL;
        } else if (monthValue == GlobalConstants.MONTH_INTVALUE_MAY) {
            month = GlobalConstants.MONTH_SHORTFORM_MAY;
        } else if (monthValue == GlobalConstants.MONTH_INTVALUE_JUNE) {
            month = GlobalConstants.MONTH_SHORTFORM_JUNE;
        } else if (monthValue == GlobalConstants.MONTH_INTVALUE_JULY) {
            month = GlobalConstants.MONTH_SHORTFORM_JULY;
        } else if (monthValue == GlobalConstants.MONTH_INTVALUE_AUGUST) {
            month = GlobalConstants.MONTH_SHORTFORM_AUGUST;
        } else if (monthValue == GlobalConstants.MONTH_INTVALUE_SEPTEMBER) {
            month = GlobalConstants.MONTH_SHORTFORM_SEPTEMBER;
        } else if (monthValue == GlobalConstants.MONTH_INTVALUE_OCTOBER) {
            month = GlobalConstants.MONTH_SHORTFORM_OCTOBER;
        } else if (monthValue == GlobalConstants.MONTH_INTVALUE_NOVEMBER) {
            month = GlobalConstants.MONTH_SHORTFORM_NOVEMBER;
        } else if (monthValue == GlobalConstants.MONTH_INTVALUE_DECEMBER) {
            month = GlobalConstants.MONTH_SHORTFORM_DECEMBER;
        }

        String[] dateArray = new String[3];
        dateArray[0] = day;
        dateArray[1] = month;
        dateArray[2] = year;

        return dateArray;
    }

    /**
     * Parses the Stings to create a {@link java.time.LocalDate}. The day- and year-String are expected to represent integers.
     * The month-String must be of the sort 'Jan', 'Feb', 'Mar', ...
     *
     * @return Returns the date associated to the given Strings.
     */
    public static LocalDate getDateFormStrings(String dayString, String monthString, String yearString, boolean useCurrentDateForFailure) {
        try {
            int day = Integer.parseInt(dayString);
            int year = Integer.parseInt(yearString);
            int month;

            if (GlobalConstants.MONTH_SHORTFORM_JANUARY.equals(monthString)) {
                month = GlobalConstants.MONTH_INTVALUE_JANUARY;
            } else if (GlobalConstants.MONTH_SHORTFORM_FEBRUARY.equals(monthString)) {
                month = GlobalConstants.MONTH_INTVALUE_FEBRURAY;
            } else if (GlobalConstants.MONTH_SHORTFORM_MARCH.equals(monthString)) {
                month = GlobalConstants.MONTH_INTVALUE_MARCH;
            } else if (GlobalConstants.MONTH_SHORTFORM_APRIL.equals(monthString)) {
                month = GlobalConstants.MONTH_INTVALUE_APRIL;
            } else if (GlobalConstants.MONTH_SHORTFORM_MAY.equals(monthString)) {
                month = GlobalConstants.MONTH_INTVALUE_MAY;
            } else if (GlobalConstants.MONTH_SHORTFORM_JUNE.equals(monthString)) {
                month = GlobalConstants.MONTH_INTVALUE_JUNE;
            } else if (GlobalConstants.MONTH_SHORTFORM_JULY.equals(monthString)) {
                month = GlobalConstants.MONTH_INTVALUE_JULY;
            } else if (GlobalConstants.MONTH_SHORTFORM_AUGUST.equals(monthString)) {
                month = GlobalConstants.MONTH_INTVALUE_AUGUST;
            } else if (GlobalConstants.MONTH_SHORTFORM_SEPTEMBER.equals(monthString)) {
                month = GlobalConstants.MONTH_INTVALUE_SEPTEMBER;
            } else if (GlobalConstants.MONTH_SHORTFORM_OCTOBER.equals(monthString)) {
                month = GlobalConstants.MONTH_INTVALUE_OCTOBER;
            } else if (GlobalConstants.MONTH_SHORTFORM_NOVEMBER.equals(monthString)) {
                month = GlobalConstants.MONTH_INTVALUE_NOVEMBER;
            } else if (GlobalConstants.MONTH_SHORTFORM_DECEMBER.equals(monthString)) {
                month = GlobalConstants.MONTH_INTVALUE_DECEMBER;
            } else {
                month = Integer.parseInt(monthString);
            }

            return LocalDate.of(year, month, day);
        } catch (NumberFormatException e) {
            // any of the parseInt methods did throw this, handle
            if(useCurrentDateForFailure) {
                return today();
            }
            throw e;
        }
    }

    public static LocalDate today() {
        return LocalDate.now(ZoneId.of(DEFAULT_TIMEZONE_ID));
    }

    public static String format(LocalDate date) {
        return dateFormatter.format(date);
    }

    public static LocalDate parse(String value, String pattern) {
        var formatter = DateTimeFormatter
            .ofPattern(pattern)
            .withZone(ZoneId.of(DEFAULT_TIMEZONE_ID));
        return LocalDate.parse(value, formatter);
    }

    public static String format(LocalDate date, String pattern) {
        var formatter = DateTimeFormatter
            .ofPattern(pattern)
            .withZone(ZoneId.of(DEFAULT_TIMEZONE_ID));
        return date.format(formatter);
    }

    public static String formatDateTime(LocalDateTime date, String pattern) {
        var formatter = DateTimeFormatter
            .ofPattern(pattern)
            .withZone(ZoneId.of(DEFAULT_TIMEZONE_ID));
        return date.format(formatter);
    }

    public static String formatMinutes(LocalDate date) {
        return minuteFormatter.format(date);
    }

    public static String formatHours(LocalDate date) {
        return hourFormatter.format(date);
    }

    public static String formatDayOfMonth(LocalDate date) {
        return dayOfMonthFormatter.format(date);
    }

    public static String formatMonth(LocalDate date) {
        return monthFormatter.format(date);
    }

    public static String formatYear(LocalDate date) {
        return yearFormatter.format(date);
    }

    public static LocalDate parse(String date, Function<ParseException, LocalDate> exceptionHandler) {
        try {
            return parse(date);
        } catch (ParseException e) {
            return exceptionHandler.apply(e);
        }
    }

    public static LocalDate parse(String date) throws ParseException {
        return LocalDate.parse(date, dateFormatter);
    }

    public static LocalDate parseOrDefault(String date, LocalDate parseExceptionValue) {
        try {
            return parse(date);
        } catch (ParseException e) {
            return parseExceptionValue;
        }
    }

    public static LocalDate parseOrNull(String date) {
        return parseOrDefault(date, null);
    }

    public static int getCurrentMinutes() {
        return now().getMinute();
    }

    public static int getCurrentHours() {
        return now().getHour();
    }

    public static YearMonth getYearMonth(LocalDate date) {
        return YearMonth.from(date);
    }

    public static LocalDate getFirstDay(YearMonth yearMonth) {
        return yearMonth.atDay(1);
    }

    public static LocalDate getLastDay(YearMonth yearMonth) {
        return yearMonth.atEndOfMonth();
    }

    public static Year getYear(LocalDate date) {
        return Year.of(date.getYear());
    }

    public static LocalDate getFirstDay(Year year) {
        return LocalDate.from(year).with(firstDayOfYear());
    }

    public static LocalDate getLastDay(Year year) {
        return LocalDate.from(year).with(lastDayOfYear());
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    public static LocalDate max(LocalDate date1, LocalDate date2) {
        if(date1.isAfter(date2)) {
            return date1;
        }
        return date2;
    }

    public static LocalDate min(LocalDate date1, LocalDate date2) {
        if(date1.isBefore(date2)) {
            return date1;
        }
        return date2;
    }

    public static int getDayOfWeek(LocalDate date) {
        return date.getDayOfWeek().getValue();
    }

    public static int getWeekdaysDistance(LocalDate begin, LocalDate end) {
        var currentDate = begin;
        int distance = 0;
        while(currentDate.isBefore(end)) {
            if(currentDate.getDayOfWeek() != SUNDAY && currentDate.getDayOfWeek() != SATURDAY) {
                distance++;
            }
            currentDate = currentDate.plusDays(1);
        }
        return distance;
    }

    public static int getMonthDays(LocalDate date) {
        return date
            .with(lastDayOfMonth())
            .getDayOfMonth();
    }

    public static LocalDate getBeginOfWeek(int year, int week) {
        return Year.of(year)
            .atDay(1)
            .with(ChronoField.ALIGNED_WEEK_OF_YEAR, week)
            .with(DayOfWeek.MONDAY);
    }

}
