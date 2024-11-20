package org.tb.dailyreport.service;

import java.time.LocalDate;
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
        for (Publicholiday newHoliday : HolidaysUtil.generateHolidays(easterSunday)) {
          publicholidayRepository.save(newHoliday);
        }
      }
    }
  }

  public List<Publicholiday> getPublicHolidaysBetween(LocalDate dateFirst, LocalDate dateLast) {
    return publicholidayDAO.getPublicHolidaysBetween(dateFirst, dateLast);
  }
}
