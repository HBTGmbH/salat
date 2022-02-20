package org.tb.persistence;

import java.util.Date;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.bdom.Publicholiday;
import org.tb.bdom.Referenceday;
import org.tb.util.DateUtils;

/**
 * DAO class for 'Referenceday'
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class ReferencedayDAO {

    private final PublicholidayDAO publicholidayDAO;
    private final ReferencedayRepository referencedayRepository;

    /**
     * Saves the given referenceday.
     */
    public void save(Referenceday ref) {
        referencedayRepository.save(ref);
    }

    /**
     * Gets the referenceday for the given date. In case the
     * referenceday does not exists, create a new one.
     */
    public Referenceday getOrAddReferenceday(Date refDate) {
        return referencedayRepository
            .findByRefdate(refDate)
            .orElseGet(() -> addReferenceday(refDate));
    }

    /**
     * Adds a referenceday to database at the time when it is first referenced in a new timereport.
     */
    public Referenceday addReferenceday(Date date) {
        Referenceday rd = new Referenceday();
        rd.setRefdate(date);

        // set day of week
        String dow = DateUtils.getDoW(date);
        rd.setDow(dow);

        // checks for public holidays
        Optional<Publicholiday> publicHoliday = publicholidayDAO.getPublicHoliday(date);
        if (publicHoliday.isPresent()) {
            rd.setHoliday(Boolean.TRUE);
            rd.setName(publicHoliday.get().getName());
        } else if (dow.equals("Sun")) {
            rd.setHoliday(Boolean.TRUE); // TODO warum ist das true?!? Also Sonntag ist ja nicht generell ein Feiertag, oder?
            rd.setName("");
        } else {
            rd.setHoliday(Boolean.FALSE);
            rd.setName("");
        }

        // check workingday
        if ((rd.getHoliday()) || (dow.equals("Sat")) || (dow.equals("Sun"))) {
            rd.setWorkingday(Boolean.FALSE);
        } else {
            rd.setWorkingday(Boolean.TRUE);
        }

        save(rd);
        return rd;
    }

}
