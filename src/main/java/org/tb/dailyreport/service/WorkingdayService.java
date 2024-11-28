package org.tb.dailyreport.service;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.tb.common.exception.ErrorCode.WD_HOLIDAY_NO_WORKED;
import static org.tb.common.exception.ErrorCode.WD_NOT_WORKED_TIMEREPORTS_FOUND;
import static org.tb.common.exception.ErrorCode.WD_OUTSIDE_CONTRACT;
import static org.tb.common.exception.ErrorCode.WD_SATSUN_NOT_WORKED;
import static org.tb.common.exception.ErrorCode.WD_UPSERT_REQ_EMPLOYEE_OR_MANAGER;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.OVERTIME_COMPENSATED;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.util.BusinessRuleCheckUtils;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.PublicholidayRepository;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.persistence.WorkingdayRepository;

@Service
@Transactional
@AllArgsConstructor
public class WorkingdayService {

  private final WorkingdayRepository workingdayRepository;
  private final PublicholidayRepository publicholidayRepository;
  private final TimereportDAO timereportDAO;
  private final AuthorizedUser authorizedUser;
  private final WorkingdayDAO workingdayDAO;

  public Workingday getWorkingday(long employeecontractId, LocalDate date) {
    return workingdayRepository.findByRefdayAndEmployeecontractId(date, employeecontractId).orElse(null);
  }

  public void upsertWorkingday(Workingday workingday) {
    var employeeId = workingday.getEmployeecontract().getEmployee().getId();
    if(!authorizedUser.isManager() && !employeeId.equals(authorizedUser.getEmployeeId())) {
      throw new AuthorizationException(WD_UPSERT_REQ_EMPLOYEE_OR_MANAGER);
    }

    BusinessRuleCheckUtils.isTrue(workingday.getEmployeecontract().isValidAt(workingday.getRefday()), WD_OUTSIDE_CONTRACT);

    if(workingday.getType() == NOT_WORKED) {
      var timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(workingday.getEmployeecontract().getId(), workingday.getRefday());
      BusinessRuleCheckUtils.empty(timereports, WD_NOT_WORKED_TIMEREPORTS_FOUND);
    }
    if(workingday.getType() == OVERTIME_COMPENSATED) {
      BusinessRuleCheckUtils.isFalse(
          workingday.getRefday().getDayOfWeek() == SATURDAY || workingday.getRefday().getDayOfWeek() == SUNDAY,
          WD_SATSUN_NOT_WORKED
      );
      BusinessRuleCheckUtils.isFalse(
          publicholidayRepository.findByRefdate(workingday.getRefday()).isPresent(),
          WD_HOLIDAY_NO_WORKED
      );
    }

    workingdayRepository.save(workingday);
  }

  public Workingday getNextRegularWorkingday(Workingday workingday) {
    Workingday nextWorkingDay = null;
    LocalDate day = workingday.getRefday();
    var employeecontractId = workingday.getEmployeecontract().getId();
    do {
      LocalDate nextDay = DateUtils.addDays(day, 1);
      if(isRegularWorkingday(nextDay)) {
        // we have found a weekday that is not a public holiday, hooray!
        var match = workingdayRepository.findByRefdayAndEmployeecontractId(nextDay, employeecontractId);
        if(match.isPresent()) {
          nextWorkingDay = match.get();
        } else {
          nextWorkingDay = new Workingday();
          nextWorkingDay.setRefday(nextDay);
          nextWorkingDay.setEmployeecontract(workingday.getEmployeecontract());
        }
      }
      day = nextDay; // prepare next iteration
    } while(nextWorkingDay == null);
    return nextWorkingDay;
  }

  public boolean isRegularWorkingday(LocalDate date) {
    if(DateUtils.isWeekday(date)) {
      Optional<Publicholiday> publicHoliday = publicholidayRepository.findByRefdate(date);
      if(publicHoliday.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  public void deleteWorkingdayById(long workingDayId) {
    workingdayRepository.deleteById(workingDayId);
  }

  public List<Workingday> getWorkingdaysByEmployeeContractId(long employeeContractId, LocalDate dateFirst,
      LocalDate dateLast) {
    return workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, dateFirst, dateLast);
  }
}
