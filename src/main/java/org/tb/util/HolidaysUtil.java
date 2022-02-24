package org.tb.util;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Scanner;
import lombok.extern.slf4j.Slf4j;
import org.tb.bdom.Publicholiday;
import org.tb.persistence.PublicholidayDAO;

/**
 * Tool class for easier handling of new holidays
 *
 * @author kd
 */
@Slf4j
public class HolidaysUtil {

    private static final String EASTERN_SUNDAYS_RESOURCE_NAME = "eastern.csv";
    private static final String DATE_FORMAT = "dd.MM.yyyy";

    /**
     * Loads the dates of the eastern sundays from <code>EASTERN_SUNDAYS_RESOURCE_NAME</code>
     */
    public static Collection<LocalDate> loadEasterSundayDates() {
        InputStream is = PublicholidayDAO.class.getClassLoader().getResourceAsStream(EASTERN_SUNDAYS_RESOURCE_NAME);
        if (is == null) {
            log.error("Could no load resource '{}'!", EASTERN_SUNDAYS_RESOURCE_NAME);
            return Collections.emptyList();
        }

        Scanner scanner = new Scanner(is);
        Collection<LocalDate> result = new ArrayList<>();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                LocalDate eastern = DateUtils.parse(line, DATE_FORMAT);
                result.add(eastern);
            } catch (IllegalArgumentException e) {
                log.error("Could not parse '{}' to date from resource/file '{}'", line, EASTERN_SUNDAYS_RESOURCE_NAME);
            }
        }
        scanner.close();

        return result;
    }

    /**
     * Generates the <code>Publicholiday</code>s for a year based on the date of eastern of that year
     */
    public static Collection<Publicholiday> generateHolidays(LocalDate easterSunday) {
        LocalDate newYear = easterSunday.withDayOfYear(1);
        LocalDate goodFriday = easterSunday.minusDays(2);
        LocalDate easterMonday = easterSunday.plusDays(1);
        LocalDate mayTheFirst = easterSunday.withMonth(5).withDayOfMonth(1);
        LocalDate ascension = easterSunday.plusDays(39);

        LocalDate whitSunday = easterSunday.plusDays(49);
        LocalDate whitMonday = easterSunday.plusDays(50);
        LocalDate reunification = easterSunday.withMonth(10).withDayOfMonth(3);
        LocalDate firstChristmasDay = easterSunday.withMonth(12).withDayOfMonth(25);
        LocalDate secondChristmasDay = easterSunday.withMonth(12).withDayOfMonth(26);
        LocalDate christmasEve = easterSunday.withMonth(12).withDayOfMonth(24);
        LocalDate newYearsEve = easterSunday.withMonth(12).withDayOfMonth(31);
        LocalDate reformationDay = easterSunday.withMonth(10).withDayOfMonth(31);

        Collection<Publicholiday> holidays = new ArrayList<>();
        holidays.add(new Publicholiday(newYear, "Neujahr"));
        holidays.add(new Publicholiday(goodFriday, "Karfreitag"));
        holidays.add(new Publicholiday(easterSunday, "Ostersonntag"));
        holidays.add(new Publicholiday(easterMonday, "Ostermontag"));
        holidays.add(new Publicholiday(mayTheFirst, "Maifeiertag"));
        holidays.add(new Publicholiday(ascension, "Christi Himmelfahrt"));
        holidays.add(new Publicholiday(whitSunday, "Pfingstsonntag"));
        holidays.add(new Publicholiday(whitMonday, "Pfingstmontag"));
        holidays.add(new Publicholiday(reunification, "Tag der Deutschen Einheit"));
        if (easterSunday.getYear() >= 2017) {
            holidays.add(new Publicholiday(reformationDay, "Reformationstag"));
        }
        holidays.add(new Publicholiday(firstChristmasDay, "1. Weihnachtstag"));
        holidays.add(new Publicholiday(secondChristmasDay, "2. Weihnachtstag"));
        holidays.add(new Publicholiday(christmasEve, "Heiligabend"));
        holidays.add(new Publicholiday(newYearsEve, "Silverster"));

        return holidays;
    }

}
