package org.tb.dailyreport.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.common.util.DateUtils;
import org.tb.common.util.HolidaysUtil;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.PublicholidayRepository;

@Service
@AllArgsConstructor
@Transactional
public class PublicholidayService {

  private final PublicholidayRepository publicholidayRepository;
  private final PublicholidayDAO publicholidayDAO;

  /**
   * Sets the German public holidays of current year if not yet done.
   * This method will be carried out once at the first login of an employee in a new year.
   */
  public void checkPublicHolidaysForCurrentYear() {
    Iterable<Publicholiday> holidays = publicholidayRepository.findAll();

    int maxYear = 0;
    for (Publicholiday holiday : holidays) {
      maxYear = Math.max(maxYear, DateUtils.getYear(holiday.getRefdate()).getValue());
    }

    for (LocalDate easterSunday : HolidaysUtil.loadEasterSundayDates()) {
      if (maxYear < easterSunday.getYear()) {
        for (Publicholiday newHoliday : generateHolidays(easterSunday)) {
          publicholidayRepository.save(newHoliday);
        }
      }
    }
  }

  public List<Publicholiday> getPublicHolidaysBetween(LocalDate dateFirst, LocalDate dateLast) {
    return publicholidayDAO.getPublicHolidaysBetween(dateFirst, dateLast);
  }

  /**
   * Generates the <code>Publicholiday</code>s for a year based on the date of eastern of that year
   */
  private List<Publicholiday> generateHolidays(LocalDate easterSunday) {
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

    List<Publicholiday> holidays = new ArrayList<>();
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
