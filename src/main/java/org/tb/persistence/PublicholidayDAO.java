package org.tb.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.Publicholiday;
import org.tb.bdom.Referenceday;
import org.tb.util.HolidaysUtil;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * DAO class for 'Publicholiday'
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class PublicholidayDAO {

    private final PublicholidayRepository publicholidayRepository;

    /**
     * Saves the given public holiday.
     */
    public void save(Publicholiday ph) {
        publicholidayRepository.save(ph);
    }

    /**
     * checks if given date is a German public holiday
     * An algorithm proposed by Gauss is used.
     *
     * @link http://www.phpforum.de/archiv_23333_Feiertage@berechnen_anzeigen.html
     */
    public Optional<Publicholiday> getPublicHoliday(Date dt) {
        return publicholidayRepository.findByRefdate(dt);
    }

    /**
     * Sets the German public holidays of current year if not yet done.
     * This method will be carried out once at the first login of an employee in a new year.
     */
    public void checkPublicHolidaysForCurrentYear() {
        Iterable<Publicholiday> holidays = publicholidayRepository.findAll();

        int maxYear = 0;
        for (Publicholiday holiday : holidays) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(holiday.getRefdate());

            maxYear = Math.max(maxYear, cal.get(Calendar.YEAR));
        }

        for (LocalDate easterSunday : HolidaysUtil.loadEasterSundayDates()) {
            if (maxYear < easterSunday.getYear()) {
                for (Publicholiday newHoliday : HolidaysUtil.generateHolidays(easterSunday)) {
                    save(newHoliday);
                }
            }
        }
    }

    /**
     * Returns a List of all {@link Publicholiday}s with a {@link Referenceday#getRefdate()} between the two given dates.
     */
    public List<Publicholiday> getPublicHolidaysBetween(Date start, Date end) {
        return publicholidayRepository.findAllByRefdateBetween(start, end);
    }

}
