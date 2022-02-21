package org.tb.util;

import static java.time.LocalTime.MIN;
import static java.time.format.TextStyle.SHORT;
import static java.util.Calendar.DATE;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;
import static java.util.Calendar.DECEMBER;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.MONDAY;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.SATURDAY;
import static java.util.Calendar.SUNDAY;
import static java.util.Calendar.WEEK_OF_YEAR;
import static java.util.Calendar.YEAR;
import static java.util.Locale.ENGLISH;
import static java.util.TimeZone.getTimeZone;
import static org.tb.GlobalConstants.DEFAULT_TIMEZONE_ID;
import static org.tb.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.GlobalConstants.STARTING_YEAR;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.tb.GlobalConstants;

@Slf4j
public class DateUtils {

    private static final ThreadLocal<DateFormat> dateFormatHolder =
        ThreadLocal.withInitial(() -> {
            DateFormat dateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
            dateFormat.setTimeZone(getTimeZone(DEFAULT_TIMEZONE_ID));
            return dateFormat;
        });
    private static final ThreadLocal<Calendar> calendarHolder = ThreadLocal.withInitial(() ->
        Calendar.getInstance(getTimeZone(DEFAULT_TIMEZONE_ID)));

    private static final String[] monthShortStrings = GlobalConstants.MONTH_SHORTFORMS;
    private static final String[] monthLongStrings = GlobalConstants.MONTH_LONGFORMS;
    private static final Map<Integer, List<OptionItem>> mapCalendarWeeks = Collections.synchronizedMap(new HashMap<>());

    private static DateFormat getDateFormat() {
        return dateFormatHolder.get();
    }

    private static Calendar getCalendar() {
        Calendar calendar = calendarHolder.get();
        calendar.setTime(new Date());
        return calendar;
    }

    /**
     * Month is 0-based.
     */
    public static Date of(int year, int month, int day) {
        Calendar calendar = getCalendar();
        calendar.set(YEAR, year);
        calendar.set(MONTH, month);
        calendar.set(DAY_OF_MONTH, day);
        return org.apache.commons.lang.time.DateUtils.truncate(calendar.getTime(), DAY_OF_MONTH);
    }

    public static String getCurrentYearString() {
        return Integer.toString(getCurrentYear());
    }

    public static int getCurrentYear() {
        return getCalendar().get(YEAR);
    }

    public static int getCurrentMonth() {
        return getCalendar().get(MONTH) + 1;
    }

    public static String getDoW(Date date) {
        Calendar calendar = getCalendar();
        calendar.setTime(date);
        int dayOfWeek = calendar.get(DAY_OF_WEEK);
        return DayOfWeek.of(dayOfWeek).getDisplayName(SHORT, ENGLISH);
    }

    public static String getYearString(Date dt) {
        Calendar calendar = getCalendar();
        calendar.setTime(dt);
        return String.valueOf(calendar.get(YEAR));
    }

    public static String getMonthShortString(Date dt) {
        Calendar calendar = getCalendar();
        calendar.setTime(dt);
        int month = calendar.get(MONTH) + 1; // Calendar has 0 - 11 months
        return Month.of(month).getDisplayName(SHORT, ENGLISH);
    }

    public static String getDayString(Date dt) {
        Calendar calendar = getCalendar();
        calendar.setTime(dt);
        int day = calendar.get(DAY_OF_MONTH);
        if (day >= 10) {
            return Integer.toString(day);
        } else {
            return "0" + day;
        }
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
            getDateFormat().parse(dateString);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    public static boolean isWeekday(Date dt) {
        Calendar calendar = getCalendar();
        calendar.setTime(dt);
        int dayOfWeek = calendar.get(DAY_OF_WEEK);
        return SATURDAY != dayOfWeek && SUNDAY != dayOfWeek;
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
    public static List<OptionItem> getYearsSinceContractStartToDisplay(Date validFrom) {
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
        try {
            Calendar calendar = getCalendar();
            int year;
            if (yearString != null) {
                year = Integer.parseInt(yearString);
                calendar.set(year, DECEMBER, 31);
            } else {
                calendar.set(MONTH, DECEMBER);
                calendar.set(DAY_OF_MONTH, 31);
                year = calendar.get(YEAR);
            }

            List<OptionItem> theList = mapCalendarWeeks.get(year);
            if (theList == null) {
                int lastWeekOfYear = calendar.get(WEEK_OF_YEAR);

                while (lastWeekOfYear == 1) {
                    calendar.add(DATE, -1);
                    lastWeekOfYear = calendar.get(WEEK_OF_YEAR);
                }

                theList = new ArrayList<>();
                for (int i = 1; i <= lastWeekOfYear; i++) {
                    calendar.set(WEEK_OF_YEAR, i);
                    calendar.set(DAY_OF_WEEK, MONDAY);
                    StringBuilder sb = new StringBuilder();
                    sb.append("KW").append(i).append(" (").append(getDateFormat().format(calendar.getTime()));
                    calendar.add(DATE, 6);
                    sb.append("-").append(getDateFormat().format(calendar.getTime())).append(")");
                    theList.add(new OptionItem(i, sb.toString()));
                }

                mapCalendarWeeks.put(year, theList);
            }
            return theList;
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }

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
    public static Date getBeginOfMonth(Date date) {
        Calendar calendar = getCalendar();
        calendar.setTime(date);
        calendar.set(DAY_OF_MONTH, 1);
        return org.apache.commons.lang.time.DateUtils.truncate(calendar.getTime(), DAY_OF_MONTH);
    }

    /**
     * gets the last day of a given month
     * E.g., month given as string '02', last day is either 28 or 29
     */
    public static Date getEndOfMonth(Date date) {
        Calendar calendar = getCalendar();
        calendar.setTime(date);
        calendar.set(DAY_OF_MONTH, calendar.getActualMaximum(DAY_OF_MONTH));
        return org.apache.commons.lang.time.DateUtils.truncate(calendar.getTime(), DAY_OF_MONTH);
    }

    /**
     * gets the last day of a given month
     * E.g., month given as string '02', last day is either 28 or 29
     */
    public static int getLastDayOfMonth(Date date) {
        Calendar calendar = getCalendar();
        calendar.setTime(date);
        return calendar.getActualMaximum(DAY_OF_MONTH);
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
     * Takes a Date and a number of days. Changes the Date by adding (amount is positive) or subtracting (amount is negative)
     * the number of days to it. For example, you have some Date and need the next day: input parameters are (date, 1).
     */
    public static Date addDays(Date originalDate, int amount) {
        Calendar calendar = getCalendar();
        calendar.setTime(originalDate);
        calendar.add(DAY_OF_MONTH, amount);
        return calendar.getTime();
    }

    public static Date addMonths(Date originalDate, int amount) {
        Calendar calendar = getCalendar();
        calendar.setTime(originalDate);
        calendar.add(MONTH, amount);
        return calendar.getTime();
    }

    /**
     * Transforms a {@link Date} into 3 {@link String}s, e.g. "09", "Feb", "2011".
     *
     * @return Returns an array of strings with the day at index 0, month at index 1 and year at index 2.
     */
    public static String[] getDateAsStringArray(Date date) {
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd");
        SimpleDateFormat monthFormat = new SimpleDateFormat("MM");
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
        String day = dayFormat.format(date);
        String year = yearFormat.format(date);
        String month = monthFormat.format(date);
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
     * Parses the Stings to create a {@link java.util.Date}. The day- and year-String are expected to represent integers.
     * The month-String must be of the sort 'Jan', 'Feb', 'Mar', ...
     *
     * @return Returns the date associated to the given Strings.
     */
    public static Date getDateFormStrings(String dayString, String monthString, String yearString, boolean useCurrentDateForFailure) {
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

            Calendar calendar = getCalendar();
            calendar.set(year, month - 1, day); // month is 0 - 11 in Calendar
            Date calculatedDate = calendar.getTime();
            return org.apache.commons.lang.time.DateUtils.truncate(calculatedDate, DAY_OF_MONTH);
        } catch (NumberFormatException e) {
            // any of the parseInt methods did throw this, handle
            if(useCurrentDateForFailure) {
                return today();
            }
            throw e;
        }
    }

    public static Date today() {
        Date date = new Date();
        return org.apache.commons.lang.time.DateUtils.truncate(date, DAY_OF_MONTH);
    }

    public static String format(Date date) {
        return getDateFormat().format(date);
    }

    public static Date parse(String date, Function<ParseException, Date> exceptionHandler) {
        try {
            return parse(date);
        } catch (ParseException e) {
            return exceptionHandler.apply(e);
        }
    }

    public static Date parse(String date) throws ParseException {
        return getDateFormat().parse(date);
    }

    public static Date parse(String date, Date parseExceptionValue) {
        try {
            return parse(date);
        } catch (ParseException e) {
            return parseExceptionValue;
        }
    }

    public static int getCurrentMinutes() {
        return getCalendar().get(MINUTE);
    }

    public static int getCurrentHours() {
        return getCalendar().get(HOUR_OF_DAY);
    }

    public static YearMonth getYearMonth(Date date) {
        Calendar calendar = getCalendar();
        calendar.setTime(date);
        int year = calendar.get(YEAR);
        int month = calendar.get(MONTH) + 1;
        return YearMonth.of(year, month);
    }

    public static Date getFirstDay(YearMonth yearMonth) {
        LocalDate localDate = LocalDate.of(yearMonth.getYear(), yearMonth.getMonthValue(), 1);
        ZonedDateTime zonedDateTime = ZonedDateTime.of(localDate, MIN, ZoneId.of(DEFAULT_TIMEZONE_ID));
        return Date.from(zonedDateTime.toInstant());
    }

    public static Date getLastDay(YearMonth yearMonth) {
        LocalDate localDate = LocalDate.of(yearMonth.getYear(), yearMonth.getMonthValue(), 1);
        localDate = localDate.withDayOfMonth(localDate.lengthOfMonth());
        ZonedDateTime zonedDateTime = ZonedDateTime.of(localDate, MIN, ZoneId.of(DEFAULT_TIMEZONE_ID));
        return Date.from(zonedDateTime.toInstant());
    }

    public static Year getYear(Date date) {
        Calendar calendar = getCalendar();
        calendar.setTime(date);
        return Year.of(calendar.get(YEAR));
    }

    public static Date getFirstDay(Year year) {
        LocalDate localDate = LocalDate.of(year.getValue(), 1, 1);
        ZonedDateTime zonedDateTime = ZonedDateTime.of(localDate, MIN, ZoneId.of(DEFAULT_TIMEZONE_ID));
        return Date.from(zonedDateTime.toInstant());
    }

    public static Date getLastDay(Year year) {
        LocalDate localDate = LocalDate.of(year.getValue(), 12, 1);
        localDate = localDate.withDayOfMonth(localDate.lengthOfMonth());
        ZonedDateTime zonedDateTime = ZonedDateTime.of(localDate, MIN, ZoneId.of(DEFAULT_TIMEZONE_ID));
        return Date.from(zonedDateTime.toInstant());
    }

    public static Date now() {
        return new Date();
    }

    public static Date max(Date date1, Date date2) {
        if(date1.compareTo(date2) > 1) {
            return date1;
        }
        return date2;
    }

    public static Date min(Date date1, Date date2) {
        if(date1.compareTo(date2) < 1) {
            return date1;
        }
        return date2;
    }

    public static int getDayOfWeek(Date date) {
        Calendar calendar = getCalendar();
        calendar.setTime(date);
        return calendar.get(DAY_OF_WEEK);
    }

    public static int getWeekdaysDistance(Date begin, Date end) {
        Calendar calendar = getCalendar();
        calendar.setTime(begin);
        int distance = 0;
        while(calendar.getTime().before(end)) {
            if(calendar.get(DAY_OF_WEEK) != SUNDAY && calendar.get(DAY_OF_WEEK) != SATURDAY) {
                distance++;
            }
            calendar.add(DATE, 1);
        }
        return distance;
    }

    public static int getMonthDays(Date date) {
        Calendar calendar = getCalendar();
        calendar.setTime(date);
        return calendar.getActualMaximum(DAY_OF_MONTH);
    }

    public static Date getBeginOfWeek(int year, int week) {
        Calendar cal = getCalendar();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.WEEK_OF_YEAR, week);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        return org.apache.commons.lang.time.DateUtils.truncate(cal.getTime(), DAY_OF_MONTH);
    }
}
