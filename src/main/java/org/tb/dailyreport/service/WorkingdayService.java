package org.tb.dailyreport.service;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.tb.common.ErrorCode.WD_HOLIDAY_NO_WORKED;
import static org.tb.common.ErrorCode.WD_NOT_WORKED_TIMEREPORTS_FOUND;
import static org.tb.common.ErrorCode.WD_SATSUN_NOT_WORKED;
import static org.tb.common.ErrorCode.WD_UPSERT_REQ_EMPLOYEE_OR_MANAGER;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.tb.auth.AuthorizedUser;
import org.tb.common.BusinessRuleChecks;
import org.tb.common.exception.AuthorizationException;
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
  private final AuthorizedUser authorizedUser;

  public void upsertWorkingday(Workingday workingday) {
    var employeeId = workingday.getEmployeecontract().getEmployee().getId();
    if(!authorizedUser.isManager() && !employeeId.equals(authorizedUser.getEmployeeId())) {
      throw new AuthorizationException(WD_UPSERT_REQ_EMPLOYEE_OR_MANAGER);
    }

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
  }

}
