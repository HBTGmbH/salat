package org.tb.util;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Scanner;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tb.bdom.Publicholiday;
import org.tb.persistence.PublicholidayDAO;

public class HolidaysUtil {

	private static final Logger LOG = LoggerFactory.getLogger(PublicholidayDAO.class); 
	private static final String EASTERN_SUNDAYS_RESOURCE_NAME = "/eastern.csv";
	private static final String DATE_FORMAT = "dd.MM.yyyy";
	
	public static Collection<LocalDate> loadEasterSundayDates() {
		InputStream is = PublicholidayDAO.class.getClassLoader().getResourceAsStream(EASTERN_SUNDAYS_RESOURCE_NAME);
		if(is == null) {
			LOG.error("Could no load resource '{}'!", EASTERN_SUNDAYS_RESOURCE_NAME);
			return Collections.emptyList();
		}
		
		DateTimeFormatter dtf = DateTimeFormat.forPattern(DATE_FORMAT);
		Scanner scanner = new Scanner(is);
		Collection<LocalDate> result = new ArrayList<LocalDate>();
		while(scanner.hasNextLine()) {
			String line = scanner.nextLine().trim();
			if(line.isEmpty() || line.startsWith("#")) continue;
			try {
				LocalDate eastern = dtf.parseLocalDate(line);
				result.add(eastern);
			} catch (IllegalArgumentException e) {
				LOG.error("Could not parse '{}' to date from resource/file '{}'", line, EASTERN_SUNDAYS_RESOURCE_NAME);
			}
		}
		scanner.close();
		
		return result;
	}
	
	/**
	 * Generates the <code>Publicholiday</code>s for a year based on the date of eastern of that year
	 * 
	 * @param easterSunday
	 * @return
	 */
	public static Collection<Publicholiday> generateHolidays(LocalDate easterSunday) {
		LocalDate newYear = easterSunday.withDayOfYear(1);
		LocalDate goodFriday = easterSunday.minusDays(2);
		LocalDate easterMonday = easterSunday.plusDays(1);
		LocalDate mayTheFirst = easterSunday.withMonthOfYear(5).withDayOfMonth(1);
		LocalDate ascension = easterSunday.plusDays(39);
		
		LocalDate whitSunday = easterSunday.plusDays(49);
		LocalDate whitMonday = easterSunday.plusDays(50);
		LocalDate reunification = easterSunday.withMonthOfYear(10).withDayOfMonth(3);
		LocalDate firstChristmasDay = easterSunday.withMonthOfYear(12).withDayOfMonth(25);
		LocalDate secondChristmasDay = easterSunday.withMonthOfYear(12).withDayOfMonth(26);
		LocalDate christmasEve = easterSunday.withMonthOfYear(12).withDayOfMonth(24);
		LocalDate newYearsEve = easterSunday.withMonthOfYear(12).withDayOfMonth(31);
		
		Collection<Publicholiday> holidays = new ArrayList<Publicholiday>();
		holidays.add(new Publicholiday(localDateToSQLDate(newYear), "Neujahr"));
		holidays.add(new Publicholiday(localDateToSQLDate(goodFriday), "Karfreitag"));
		holidays.add(new Publicholiday(localDateToSQLDate(easterSunday), "Ostersonntag"));
		holidays.add(new Publicholiday(localDateToSQLDate(easterMonday), "Ostermontag"));
		holidays.add(new Publicholiday(localDateToSQLDate(mayTheFirst), "Maifeiertag"));
		holidays.add(new Publicholiday(localDateToSQLDate(ascension), "Christi Himmelfahrt"));
		holidays.add(new Publicholiday(localDateToSQLDate(whitSunday), "Pfingstsonntag"));
		holidays.add(new Publicholiday(localDateToSQLDate(whitMonday), "Pfingstmontag"));
		holidays.add(new Publicholiday(localDateToSQLDate(reunification), "Tag der Deutschen Einheit"));
		holidays.add(new Publicholiday(localDateToSQLDate(firstChristmasDay), "1. Weihnachtstag"));
		holidays.add(new Publicholiday(localDateToSQLDate(secondChristmasDay), "2. Weihnachtstag"));
		holidays.add(new Publicholiday(localDateToSQLDate(christmasEve), "Heiligabend"));
		holidays.add(new Publicholiday(localDateToSQLDate(newYearsEve), "Silverster"));
		
		return holidays;
	}
	
	private static java.sql.Date localDateToSQLDate(LocalDate input) {
		return new java.sql.Date(input.toDate().getTime());
	}
}
