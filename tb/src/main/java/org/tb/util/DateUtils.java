package org.tb.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tb.GlobalConstants;

/**
 * @author oda
 * 
 * The important thing to know for most of the methods extracting date info is:
 * 	- java.utilDate.toString() looks like: 'Wed Aug 16 00:00:00 CET 2006' or
 * 										   'Wed Aug 16 00:00:00 CEST 2006' (daylight saving time!)
 * 	- java.sql.Date.toString()looks like:  '2006-08-16'
 *
 */
public class DateUtils {
    
    private static String[] monthShortStrings = GlobalConstants.MONTH_SHORTFORMS;
    
    private static String[] monthLongStrings = GlobalConstants.MONTH_LONGFORMS;
    
    private static Map<Integer, List<OptionItem>> mapCalendarWeeks = Collections.synchronizedMap(new HashMap<Integer, List<OptionItem>>());
    
    /**
     * Gets the date without the time value.
     */
    public static Date stripTime(java.util.Date timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
    
    public static String getCurrentDateString() {
        // returns date as EEEE yyyy-MM-dd
        Date dt = new Date();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String currentDate = dt.toString().substring(0, 4) + df.format(dt);
        return currentDate;
    }
    
    public static String getCurrentYearString() {
        // returns yyyy
        Date dt = new Date();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        return df.format(dt).substring(0, 4);
    }
    
    public static int getCurrentYear() {
        // returns yyyy as int
        return Integer.parseInt(getCurrentYearString());
    }
    
    public static String getCurrentMonthString() {
        // returns MM as string
        Date dt = new Date();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        return df.format(dt).substring(5, 7);
    }
    
    public static int getCurrentMonth() {
        // returns MM as int
        return Integer.parseInt(getCurrentMonthString());
    }
    
    public static String getDateString(java.util.Date dt) {
        // returns date as EEEE yyyy-MM-dd from java.util.Date !!
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String currentDate = dt.toString().substring(0, 4) + df.format(dt);
        return currentDate;
    }
    
    public static String getYearString(java.util.Date dt) {
        // returns yyyy
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        return df.format(dt).substring(0, 4);
    }
    
    public static String getMonthString(java.util.Date dt) {
        // returns MM as string
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        return df.format(dt).substring(5, 7);
    }
    
    public static int getMonth(java.util.Date dt) {
        // returns MM as int
        return Integer.parseInt(getMonthString(dt));
    }
    
    public static String getMonthShortString(java.util.Date dt) {
        // returns EEE from date (e.g., 'Jan')
        return dt.toString().substring(4, 7);
        //		return (getDateString(dt).substring(4,7));
    }
    
    public static String getMonthShortString(java.sql.Date dt) {
        // returns EEE from date (e.g., 'Jan')
        Date utilDate = new Date(dt.getTime()); // convert to java.util.Date
        return utilDate.toString().substring(4, 7);
    }
    
    public static String getDayString(java.util.Date dt) {
        // returns dd as string
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        return df.format(dt).substring(8, 10);
    }
    
    public static int getDay(java.util.Date dt) {
        // returns dd as int
        return Integer.parseInt(getMonthString(dt));
    }
    
    public static int getMonthMMFromShortstring(String st) {
        // returns MM as int from short string (e.g., '01' from 'Jan')
        int index = 0;
        for (int i = 0; i < monthShortStrings.length; i++) {
            if (st.equals(monthShortStrings[i])) {
                index = i + 1;
                return index;
            }
        }
        return index;
    }
    
    public static String getMonthMMStringFromShortstring(String st) {
        // returns MM as string from short string (e.g., '01' from 'Jan')
        int index = getMonthMMFromShortstring(st);
        String mmString = "";
        if (index > 0 && index < 10) {
            mmString = "0" + index;
        } else {
            mmString = "" + index;
        }
        
        return mmString;
    }
    
    public static int getYear(java.util.Date dt) {
        // returns yyyy as int from date
        return Integer.parseInt(getYearString(dt));
    }
    
    public static int getYear(String st) {
        // returns yyyy as int from String
        return Integer.parseInt(st);
    }
    
    public static String getSqlDateString(String eeeyyyymmdd) {
        // gets format yyyy-mm-dd from eee yyyy-mm-dd
        return eeeyyyymmdd.substring(4, eeeyyyymmdd.length());
    }
    
    public static String getSqlDateString(java.util.Date utilDate) {
        // gets sql date in format yyyy-mm-dd from java.util.Date
        int length = utilDate.toString().length();
        String sqlDateString = utilDate.toString().substring(length - 4, length) + "-" +
                getMonthMMStringFromShortstring(utilDate.toString().substring(4, 7)) + "-" +
                utilDate.toString().substring(8, 10);
        
        return sqlDateString;
    }
    
    /**
     * validates if date string has correct sql date format 'yyyy-mm-dd'
     * 
     * @param dateString
     * 
     * @return boolean
     */
    public static boolean validateDate(String dateString) {
        boolean dateError = false;
        if (dateString.length() != 10) {
            dateError = true;
        }
        if (dateError != true) {
            char[] chArr = dateString.toCharArray();
            if (!Character.isDigit(chArr[0]) ||
                    !Character.isDigit(chArr[1]) ||
                    !Character.isDigit(chArr[2]) ||
                    !Character.isDigit(chArr[3]) ||
                    chArr[4] != '-' ||
                    !Character.isDigit(chArr[5]) ||
                    !Character.isDigit(chArr[6]) ||
                    chArr[7] != '-' ||
                    !Character.isDigit(chArr[8]) ||
                    !Character.isDigit(chArr[9])) {
                dateError = true;
            }
        }
        return dateError;
    }
    
    public static String getDow(java.sql.Date dt) {
        // get weekday
        Date utilDate = new Date(dt.getTime()); // convert to java.util.Date
        String dow = utilDate.toString().substring(0, 3);
        
        return dow;
    }
    
    /*
     * builds up a list of string with current and previous year
     */
    public static List<OptionItem> getYearsToDisplay() {
        List<OptionItem> theList = new ArrayList<OptionItem>();
        
        for (int i = GlobalConstants.STARTING_YEAR; i <= getCurrentYear() + 1; i++) {
            String yearString = "" + i;
            theList.add(new OptionItem(yearString, yearString));
        }
        
        return theList;
    }
    
    /*
     * builds up a list of string with current and previous years since startyear of contract
     */
    public static List<OptionItem> getYearsSinceContractStartToDisplay(Date validFrom) {
        List<OptionItem> theList = new ArrayList<OptionItem>();
        
        int startyear = Integer.parseInt(getYearString(validFrom));
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
        List<OptionItem> theList = new ArrayList<OptionItem>();
        String dayValue = "";
        String dayLabel = "";
        for (int i = 1; i <= 12; i++) {
            dayValue = monthShortStrings[i - 1];
            dayLabel = monthLongStrings[i - 1];
            theList.add(new OptionItem(dayValue, dayLabel));
        }
        
        return theList;
    }
    
    /**
     * builds up a list of calendar weeks for a certain year
     * 
     * @param yearString
     * @return
     */
    public static List<OptionItem> getWeeksToDisplay(String yearString) {
    	try {
    		Calendar calendar = Calendar.getInstance();
    		Integer year = null;
    		if(yearString != null) {
	    		year = Integer.parseInt(yearString);
	    		calendar.set(year, 11, 31);
    		} else {
    			calendar.set(Calendar.MONTH, 11);
    			calendar.set(Calendar.DAY_OF_MONTH, 31);
    			year = calendar.get(Calendar.YEAR);
    		}
    		
    		List<OptionItem> theList = mapCalendarWeeks.get(year);
    		if(theList == null) {
	    		int lastWeekOfYear = calendar.get(Calendar.WEEK_OF_YEAR);
	    		SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
	    		
	    		theList = new ArrayList<OptionItem>();
	    		for(int i = 1; i <= lastWeekOfYear; i++) {
	    			calendar.set(Calendar.WEEK_OF_YEAR, i);
	    			calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
	    			StringBuilder sb = new StringBuilder();
	    			sb.append("KW").append(i).append(" (").append(sdf.format(calendar.getTime()));
	    			calendar.add(Calendar.DATE, 6);
	    			sb.append("-").append(sdf.format(calendar.getTime())).append(")");
	    			theList.add(new OptionItem(i, sb.toString()));
	    		}
	    		
	    		mapCalendarWeeks.put(year, theList);
    		}
    		return theList;
    	} catch(NumberFormatException e) {
    		return Collections.emptyList();
    	} 
    	
    }
    
    /*
     * builds up a list of string with days to display (01-31)
     */
    public static List<OptionItem> getDaysToDisplay() {
        List<OptionItem> theList = new ArrayList<OptionItem>();
        for (int i = 1; i <= 31; i++) {
        	theList.add(intToOptionitem(i));
        }
        return theList;
    }
    
    /*
     * builds up a list of string with hour to display (6-21)
     */
    public static List<OptionItem> getHoursToDisplay() {
        List<OptionItem> theList = new ArrayList<OptionItem>();
        for (int i = 6; i < 22; i++) {
        	theList.add(intToOptionitem(i));
        }
        return theList;
    }
    
    /*
     * builds up a list of string with hour to display (1-5)
     */
    public static List<OptionItem> getCompleteHoursToDisplay() {
        List<OptionItem> theList = new ArrayList<OptionItem>();
        for (int i = 0; i <= 5; i++) {
        	theList.add(intToOptionitem(i));
        }
        return theList;
    }
    
    /*
     * builds up a list of string with duration hours to display (0-15)
     */
    public static List<OptionItem> getHoursDurationToDisplay() {
        List<OptionItem> theList = new ArrayList<OptionItem>();
        for (int i = 0; i <= 24; i++) {
        	theList.add(intToOptionitem(i));
        }
        return theList;
    }
    
    /*
     * builds up a list of string with minutes to display (05-55)
     */
    public static List<OptionItem> getMinutesToDisplay() {
        List<OptionItem> theList = new ArrayList<OptionItem>();
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
     * gets date of easter for a given year as int array [year, month, day]
     * current year is taken from input Date
     * 
     * @param Date dt
     * 
     * @return int[] easter
     */
    public static int[] getEaster(Date dt) {
        
        int[] easter = new int[3];
        int year = getYear(dt);
        
        int a, b, c, d, e;
        
        a = year % 19;
        b = year % 4;
        c = year % 7;
        int m = (8 * (year / 100) + 13) / 25 - 2;
        int s = year / 100 - year / 400 - 2;
        int M = (15 + s - m) % 30;
        int N = (6 + s) % 7;
        d = (M + 19 * a) % 30;
        
        int D;
        if (d == 29) {
            D = 28;
        } else if (d == 28 && a >= 11) {
            D = 27;
        } else {
            D = d;
        }
        
        e = (2 * b + 4 * c + 6 * D + N) % 7;
        
        //int delta = D + e + 1;
        int delta = D + e + 1 + 21;
        
        easter[0] = year;
        if (delta > 31) {
            easter[1] = 4; // April
            easter[2] = delta - 31;
        } else {
            easter[1] = 3; // April
            easter[2] = delta;
        }
        
        return easter;
    }
    
    /**
     * gets the last day of a given month
     * E.g., month given as string '02', last day is either 28 or 29
     * 
     * @param month
     * @return
     */
    public static int getLastDayOfMonth(String year, String month) {
        int result = -1;
        int imonth = Integer.parseInt(month);
        
        if (imonth == 1 || imonth == 3 || imonth == 5 ||
                imonth == 7 || imonth == 8) {
            result = 31;
        }
        if (imonth == 4 || imonth == 6 || imonth == 9 ||
                imonth == 11) {
            result = 30;
        }
        if (imonth == 2) {
            if (isLeapYear(year)) {
                result = 29;
            } else {
                result = 28;
            }
        }
        
        return result;
    }
    
    /**
     * checks if given year is a leap year
     * 
     * @param String year
     * 
     * @return boolean
     */
    public static boolean isLeapYear(String year) {
        boolean leapYear = false;
        int iyear = Integer.parseInt(year);
        
        if (iyear % 4 == 0) {
            leapYear = true;
        }
        if (iyear % 100 == 0) {
            leapYear = false;
        }
        if (iyear % 400 == 0) {
            leapYear = true;
        }
        
        return leapYear;
    }
    
    /**
     * calculates worktime from begin/end times in a form
     * 
     * @param int hrbegin
     * @param int minbegin
     * @param int hrend
     * @param int minend
     * 
     * @return double - decimal hours
     */
    public static double calculateTime(int hrbegin, int minbegin, int hrend, int minend) {
        double worktime = 0.0;
        
        int hours = hrend - hrbegin;
        int minutes = minend - minbegin;
        
        if (minutes < 0) {
            hours -= 1;
            minutes += 60;
        }
        worktime = hours * 1. + minutes / 60.;
        
        return worktime;
    }
    
    /**
     * Takes a Date and a number of days. Changes the Date by adding (changeDays is positive) or subtracting (changeDays is negative)
     *  the number of days to it. For example, you have some Date and need the next day: input parameters are (date, 1).
     * 
     * @param originalDate
     * @param changeDays
     * @return Date
     */
    public static Date addDays(Date originalDate, int changeDays) {
        
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(originalDate);
        calendar.add(Calendar.DATE, changeDays);
        
        Date date = calendar.getTime();
        
        return date;
    }
}
