package org.tb.dailyreport.service;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.tb.common.ErrorCode.WD_HOLIDAY_NO_WORKED;
import static org.tb.common.ErrorCode.WD_NOT_WORKED_TIMEREPORTS_FOUND;
import static org.tb.common.ErrorCode.WD_SATSUN_NOT_WORKED;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.tb.common.BusinessRuleChecks;
import org.tb.dailyreport.domain.WorkingDayValidationError;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.PublicholidayRepository;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayRepository;

@Service
@AllArgsConstructor
public class WorkingdayService {

  private final WorkingdayRepository workingdayRepository;
  private final PublicholidayRepository publicholidayRepository;
  private final TimereportDAO timereportDAO;

  public WorkingDayValidationError upsertWorkingday(Workingday workingday) {
    if(workingday.getType() == NOT_WORKED) {
      var timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(workingday.getEmployeecontract().getId(), workingday.getRefday());
      BusinessRuleChecks.empty(timereports, WD_NOT_WORKED_TIMEREPORTS_FOUND);
    }
    BusinessRuleChecks.isFalse(
        workingday.getRefday().getDayOfWeek() == SATURDAY || workingday.getRefday().getDayOfWeek() == SUNDAY,
        WD_SATSUN_NOT_WORKED
    );
    BusinessRuleChecks.isFalse(
        publicholidayRepository.findByRefdate(workingday.getRefday()).isPresent(),
        WD_HOLIDAY_NO_WORKED
    );

    workingdayRepository.save(workingday);
    return null;
  }

}
