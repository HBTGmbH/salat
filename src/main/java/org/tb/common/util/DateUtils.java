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
import static org.tb.common.GlobalConstants.DEFAULT_TIMEZONE_ID;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.function.Function;
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

    public static boolean validateDateTime(String dateTimeString, String pattern) {
        var formatter = DateTimeFormatter
            .ofPattern(pattern)
            .withZone(ZoneId.of(DEFAULT_TIMEZONE_ID));
        try {
            formatter.parse(dateTimeString);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public static boolean isWeekday(LocalDate dt) {
        var dow = dt.getDayOfWeek();
        return dow != SATURDAY && dow != SUNDAY;
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

    public static String formatMinutes(LocalDateTime date) {
        return minuteFormatter.format(date);
    }

    public static String formatHours(LocalDateTime date) {
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

    public static LocalDate parse(String date, Function<DateTimeParseException, LocalDate> exceptionHandler) {
        try {
            return parse(date);
        } catch (DateTimeParseException e) {
            return exceptionHandler.apply(e);
        }
    }

    public static LocalDate parse(String date) {
        return LocalDate.parse(date, dateFormatter);
    }

    public static LocalDateTime parseDateTime(String dateTime, String pattern) {
        var formatter = DateTimeFormatter
            .ofPattern(pattern)
            .withZone(ZoneId.of(DEFAULT_TIMEZONE_ID));
        return LocalDateTime.parse(dateTime, formatter);
    }

    public static LocalDate parseOrDefault(String date, LocalDate parseExceptionValue) {
        try {
            return parse(date);
        } catch (DateTimeParseException e) {
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

    /**
     * Gets the distance of working days between the two dates. Same dates will return 1 if the dates are
     * working days.
     */
    public static long getWorkingDayDistance(LocalDate begin, LocalDate end) {
        var currentDate = begin;
        long distance = 0;
        do {
            if(currentDate.getDayOfWeek() != SUNDAY && currentDate.getDayOfWeek() != SATURDAY) {
                distance++;
            }
            currentDate = currentDate.plusDays(1);
        } while(!currentDate.isAfter(end));
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
