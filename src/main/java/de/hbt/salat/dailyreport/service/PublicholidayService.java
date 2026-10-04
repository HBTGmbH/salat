package de.hbt.salat.dailyreport.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.event.AuthorizedUserChangedEvent;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.common.util.HolidaysUtil;
import de.hbt.salat.dailyreport.domain.Publicholiday;
import de.hbt.salat.dailyreport.persistence.PublicholidayDAO;
import de.hbt.salat.dailyreport.persistence.PublicholidayRepository;
import de.hbt.salat.dailyreport.persistence.ReferencedayRepository;

@Service
@AllArgsConstructor
@Transactional
@Authorized
public class PublicholidayService {

  static final int LAST_WORKDAY_LOOKBACK_DAYS = 14;

  private final PublicholidayRepository publicholidayRepository;
  private final PublicholidayDAO publicholidayDAO;
  private final ReferencedayRepository referencedayRepository;

  /**
   * Sets the German public holidays of current year if not yet done.
   * This method will be carried out once at the first login of an employee in a new year.
   *
   * <p>A reference day of such a date may already exist — somebody booked ahead before the year's holidays were
   * there. It takes the holiday over right away, since its calendar columns follow {@code publicholiday} (#1211).
   */
  @EventListener
  public void checkPublicHolidaysForCurrentYear(AuthorizedUserChangedEvent event) {
    Iterable<Publicholiday> holidays = publicholidayRepository.findAll();

    int maxYear = 0;
    for (Publicholiday holiday : holidays) {
      maxYear = Math.max(maxYear, DateUtils.getYear(holiday.getRefdate()).getValue());
    }

    for (LocalDate easterSunday : HolidaysUtil.loadEasterSundayDates()) {
      if (maxYear < easterSunday.getYear()) {
        var newHolidays = generateHolidays(easterSunday);
        publicholidayRepository.saveAll(newHolidays);
        applyToReferencedays(newHolidays);
      }
    }
  }

  private void applyToReferencedays(List<Publicholiday> holidays) {
    Map<LocalDate, String> names = holidays.stream()
        .collect(Collectors.toMap(Publicholiday::getRefdate, Publicholiday::getName));
    referencedayRepository.findAllByRefdateIn(names.keySet())
        .forEach(referenceday -> referenceday.applyCalendar(names.get(referenceday.getRefdate())));
  }

  public List<Publicholiday> getPublicHolidaysBetween(LocalDate dateFirst, LocalDate dateLast) {
    return publicholidayDAO.getPublicHolidaysBetween(dateFirst, dateLast);
  }

  /**
   * The dates of the public holidays in the range, both ends included — what another module counts
   * working days with, without the entity leaving this module (#1338, ADR-0021).
   */
  @Transactional(readOnly = true)
  public Set<LocalDate> getPublicHolidayDatesBetween(LocalDate dateFirst, LocalDate dateLast) {
    return publicholidayDAO.getPublicHolidaysBetween(dateFirst, dateLast).stream()
        .map(Publicholiday::getRefdate)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * The last working day before {@code day}, weekends and public holidays skipped — "letzter
   * Arbeitstag" of the command palette (#1158). The holidays of the fortnight before are read at
   * once; no run of free days is longer than that.
   */
  @Transactional(readOnly = true)
  public LocalDate getLastWorkdayBefore(LocalDate day) {
    var holidays = publicholidayDAO.getPublicHolidaysBetween(day.minusDays(LAST_WORKDAY_LOOKBACK_DAYS), day.minusDays(1))
        .stream().map(Publicholiday::getRefdate).collect(Collectors.toSet());
    var candidate = day.minusDays(1);
    while (!DateUtils.isWeekday(candidate) || holidays.contains(candidate)) {
      candidate = candidate.minusDays(1);
    }
    return candidate;
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
