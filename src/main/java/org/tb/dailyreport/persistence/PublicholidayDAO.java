package org.tb.dailyreport.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.Referenceday;

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
     * Returns a List of all {@link Publicholiday}s with a {@link Referenceday#getRefdate()} between the two given dates.
     */
    public List<Publicholiday> getPublicHolidaysBetween(LocalDate start, LocalDate end) {
        return publicholidayRepository.findAllByRefdateBetween(start, end);
    }

}
