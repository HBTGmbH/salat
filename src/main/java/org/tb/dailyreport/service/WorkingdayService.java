package org.tb.dailyreport.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.tb.common.BusinessRuleChecks;
import org.tb.common.ErrorCode;
import org.tb.dailyreport.domain.WorkingDayValidationError;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayRepository;

@Service
@AllArgsConstructor
public class WorkingdayService {

  private final WorkingdayRepository workingdayRepository;
  private final TimereportDAO timereportDAO;

  public WorkingDayValidationError upsertWorkingday(Workingday workingday) {
    if(workingday.getType() == WorkingDayType.NOT_WORKED) {
      var timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(workingday.getEmployeecontract().getId(), workingday.getRefday());
      BusinessRuleChecks.empty(timereports, ErrorCode.WD_NOT_WORKED_TIMEREPORTS_FOUND);
    }
    workingdayRepository.save(workingday);
    return null;
  }

}
