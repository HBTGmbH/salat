package org.tb.persistence;

import java.time.LocalDate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.bdom.Publicholiday;
import org.tb.bdom.Referenceday;
import org.tb.util.DateUtils;
import org.tb.util.HolidaysUtil;

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
    public Optional<Publicholiday> getPublicHoliday(LocalDate dt) {
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
            maxYear = Math.max(maxYear, DateUtils.getYear(holiday.getRefdate()).getValue());
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
    public List<Publicholiday> getPublicHolidaysBetween(LocalDate start, LocalDate end) {
        return publicholidayRepository.findAllByRefdateBetween(start, end);
    }

}
