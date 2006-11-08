package org.tb.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

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
	
	public static String[] monthShortStrings = {
			"Jan", "Feb", "Mar", "Apr", "May", "Jun",
			"Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
	};
	
	public static String[] monthLongStrings = {
		"January", "February", "March", "April", "May", "June",
		"July", "August", "September", "October", "November", "December"
};
	
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
		SimpleDateFormat df = new SimpleDateFormat( "yyyy-MM-dd" );
		String currentDate = dt.toString().substring(0,4) + df.format(dt);
		return currentDate;
	}
	
	public static String getCurrentYearString() {
		// returns yyyy
		Date dt = new Date();
		SimpleDateFormat df = new SimpleDateFormat( "yyyy-MM-dd" );
		return df.format(dt).substring(0,4);
	}
	
	public static int getCurrentYear() {
		// returns yyyy as int
		return (Integer.parseInt(getCurrentYearString()));
	}
	
	public static String getCurrentMonthString() {
		// returns MM as string
		Date dt = new Date();
		SimpleDateFormat df = new SimpleDateFormat( "yyyy-MM-dd" );
		return df.format(dt).substring(5,7);
	}
	
	public static int getCurrentMonth() {
		// returns MM as int
		return (Integer.parseInt(getCurrentMonthString()));
	}
	
	public static String getDateString(java.util.Date dt) {
		// returns date as EEEE yyyy-MM-dd from java.util.Date !!
		SimpleDateFormat df = new SimpleDateFormat( "yyyy-MM-dd" );
		String currentDate = dt.toString().substring(0,4) + df.format(dt);
		return currentDate;
	}
	
	public static String getYearString(java.util.Date dt) {
		// returns yyyy
		SimpleDateFormat df = new SimpleDateFormat( "yyyy-MM-dd" );
		return df.format(dt).substring(0,4);
	}
	
	public static String getMonthString(java.util.Date dt) {
		// returns MM as string
		SimpleDateFormat df = new SimpleDateFormat( "yyyy-MM-dd" );
		return df.format(dt).substring(5,7);
	}
	
	public static int getMonth(java.util.Date dt) {
		// returns MM as int
		return (Integer.parseInt(getMonthString(dt)));
	}
	
	public static String getMonthShortString(java.util.Date dt) {
		// returns EEE from date (e.g., 'Jan')
		return (getDateString(dt).substring(0,3));
	}
	
	public static String getMonthShortString(java.sql.Date dt) {
		// returns EEE from date (e.g., 'Jan')
		Date utilDate = new Date(dt.getTime()); // convert to java.util.Date
		return (utilDate.toString().substring(4,7));
	}
	
	public static String getDayString(java.util.Date dt) {
		// returns dd as string
		SimpleDateFormat df = new SimpleDateFormat( "yyyy-MM-dd" );
		return df.format(dt).substring(8,10);
	}
	
	public static int getDay(java.util.Date dt) {
		// returns dd as int
		return (Integer.parseInt(getMonthString(dt)));
	}
	
	public static int getMonthMMFromShortstring(String st) {
		// returns MM as int from short string (e.g., '01' from 'Jan')
		int index = 0;
		for (int i=0; i<monthShortStrings.length; i++) {
			if (st.equals(monthShortStrings[i])) {
				index = i+1;
				return index;
			}
		}
		return index;
	}
	
	public static String getMonthMMStringFromShortstring(String st) {
		// returns MM as string from short string (e.g., '01' from 'Jan')
		int index = getMonthMMFromShortstring(st);
		String mmString = "";
		if ((index > 0) && (index < 10)) {
			mmString = "0" + index; 
		} else {
			mmString = "" + index;
		}
	
		return mmString;
	}
	
	public static int getYear(java.util.Date dt) {
		// returns yyyy as int from date
		return (Integer.parseInt(getYearString(dt)));
	}
	
	public static int getYear(String st) {
		// returns yyyy as int from String
		return (Integer.parseInt(st));
	}
	
	public static String getSqlDateString(String eeeyyyymmdd) {
		// gets format yyyy-mm-dd from eee yyyy-mm-dd
		return eeeyyyymmdd.substring(4,eeeyyyymmdd.length());
	}
	
	public static String getSqlDateString(java.util.Date utilDate) {
		// gets sql date in format yyyy-mm-dd from java.util.Date
		int length = utilDate.toString().length();
		String sqlDateString = utilDate.toString().substring(length-4,length) + "-" +
								getMonthMMStringFromShortstring(utilDate.toString().substring(4,7)) + "-" +
								utilDate.toString().substring(8,10);
				
		return sqlDateString;
	}
	
	public static java.sql.Date getSqlDate(String eeeyyyymmdd) {
		// gets sql date in format yyyy-mm-dd from string eee yyyy-mm-dd
		java.sql.Date theDate = java.sql.Date.valueOf(getSqlDateString(eeeyyyymmdd));		
		return theDate;
	}
	
	public static java.sql.Date getSqlDate(java.util.Date utilDate) {
		// gets sql date in format yyyy-mm-dd from java.util.Date
		int length = utilDate.toString().length();
		String sqlDateString = utilDate.toString().substring(length-4,length) + "-" +
								getMonthMMStringFromShortstring(utilDate.toString().substring(4,7)) + "-" +
								utilDate.toString().substring(8,10);
		java.sql.Date theDate = java.sql.Date.valueOf(sqlDateString);		
		return theDate;
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
		if (dateString.length() != 10) dateError = true;
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
				!Character.isDigit(chArr[9]) ) {
				dateError = true;
			}
		}
		return dateError;
	}
	
	public static String getDow(java.sql.Date dt) {	
		// get weekday
		Date utilDate = new Date(dt.getTime()); // convert to java.util.Date
		String dow = utilDate.toString().substring(0,3);

		return dow;
	}
	
	/*
	 * builds up a list of string with current and previous year
	 */
	public static List getYearsToDisplay() {
		List theList = new ArrayList();
		
		for (int i=0; i<2; i++) {
			int year = getCurrentYear() - i;
			String yearString = "" + year;
			theList.add(new OptionItem(yearString, yearString));
		}
				
		return theList;
	}
	
	/*
	 * builds up a list of string with months to display (Jan-Dec)
	 */
	public static List getMonthsToDisplay() {
		List theList = new ArrayList();
		String dayValue = "";
		String dayLabel = "";
		for (int i=1; i<=12; i++) {
			dayValue = monthShortStrings[i-1];
			dayLabel = monthLongStrings[i-1];
			theList.add(new OptionItem(dayValue, dayLabel));
		}
		
		return theList;
	}
	
	/*
	 * builds up a list of string with days to display (01-31)
	 */
	public static List getDaysToDisplay() {
		List theList = new ArrayList();
		String dayValue = "";
		String dayLabel = "";
		for (int i=1; i<=31; i++) {
			
			if (i<10) {
				dayLabel = "0" + i;
				dayValue = "0" + i;
			}
			if (i>=10) {
				dayLabel = "" + i;
				dayValue = "" + i;
			}
			theList.add(new OptionItem(dayValue, dayLabel));
		}
					
		return theList;
	}
	
	/*
	 * builds up a list of string with hour to display (6-21)
	 */
	public static List getHoursToDisplay() {
		List theList = new ArrayList();
		String hourValue = "";
		String hourLabel = "";
		for (int i=6; i<22; i++) {
			hourValue = "" + i;
			if (i<10) hourLabel = "0" + i;
			if (i>=10) hourLabel = "" + i;
			theList.add(new OptionItem(hourValue, hourLabel));
		}
		return theList;
	}
	
	/*
	 * builds up a list of string with hour to display (1-5)
	 */
	public static List getCompleteHoursToDisplay() {
		List theList = new ArrayList();
		String hourValue = "";
		String hourLabel = "";
		for (int i=0; i<=5; i++) {
			hourValue = "" + i;
			hourLabel = "0" + i;
			theList.add(new OptionItem(hourValue, hourLabel));
		}
		return theList;
	}
	
	/*
	 * builds up a list of string with duration hours to display (0-15)
	 */
	public static List getHoursDurationToDisplay() {
		List theList = new ArrayList();
		String hourValue = "";
		String hourLabel = "";
		for (int i=0; i<16; i++) {
			hourValue = "" + i;
			if (i<10) hourLabel = "0" + i;
			if (i>=10) hourLabel = "" + i;
			theList.add(new OptionItem(hourValue, hourLabel));
		}
		
		
				
		return theList;
	}
	
	/*
	 * builds up a list of string with minutes to display (05-55)
	 */
	public static List getMinutesToDisplay() {
		List theList = new ArrayList();
		String minuteValue = "";
		String minuteLabel = "";
		
		for (int i=0; i<60; i+=5) {
			minuteValue = "" + i;
			if (i<10) minuteLabel = "0" + i;
			if (i>=10) minuteLabel = "" + i;
			theList.add(new OptionItem(minuteValue, minuteLabel));
		}
				
		return theList;
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
		int m = ((8 * (year / 100) + 13) / 25) - 2;
		int s = (year / 100) - (year / 400) - 2;
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

		if ((imonth == 1) || (imonth == 3) || (imonth == 5) ||
			(imonth == 7) || (imonth == 8)) {
			result = 31;
		}
		if ((imonth == 4) || (imonth == 6) || (imonth == 9) ||
			(imonth == 11)) {
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
		
		if (iyear % 4 == 0) leapYear = true; 
		if (iyear % 100 == 0) leapYear = false; 
		if (iyear % 400 == 0) leapYear = true; 
		
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
		worktime = hours*1. + minutes/60.;
		
		return worktime;
	}
}
