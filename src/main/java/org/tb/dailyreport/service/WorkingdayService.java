package org.tb.dailyreport.service;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.tb.auth.domain.AccessLevel.WRITE;
import static org.tb.common.exception.ErrorCode.WD_DELETE_REQ_EMPLOYEE_OR_MANAGER;
import static org.tb.common.exception.ErrorCode.WD_HOLIDAY_NO_WORKED;
import static org.tb.common.exception.ErrorCode.WD_NOT_WORKED_TIMEREPORTS_FOUND;
import static org.tb.common.exception.ErrorCode.WD_OUTSIDE_CONTRACT;
import static org.tb.common.exception.ErrorCode.WD_READ_REQ_EMPLOYEE_OR_MANAGER;
import static org.tb.common.exception.ErrorCode.WD_SATSUN_NOT_WORKED;
import static org.tb.common.exception.ErrorCode.WD_UPSERT_REQ_EMPLOYEE_OR_MANAGER;
import static org.tb.common.util.DateUtils.today;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.OVERTIME_COMPENSATED;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.util.BusinessRuleCheckUtils;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.PublicholidayRepository;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.persistence.WorkingdayRepository;
import org.tb.employee.event.EmployeecontractConflictResolutionEvent;
import org.tb.employee.event.EmployeecontractDeleteEvent;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Employeeorder;

@Service
@Transactional
@AllArgsConstructor
@Authorized
public class WorkingdayService {

  private static final String AUTH_CATEGORY_WORKINGDAY = "WORKINGDAY";

  private final WorkingdayRepository workingdayRepository;
  private final PublicholidayRepository publicholidayRepository;
  private final TimereportDAO timereportDAO;
  private final AuthorizedUser authorizedUser;
  private final WorkingdayDAO workingdayDAO;
  private final AuthService authService;
  private final EmployeecontractService employeecontractService;

  public Workingday getWorkingday(long employeecontractId, LocalDate date) {
    var employeecontract = employeecontractService.getEmployeecontractById(employeecontractId);
    var employeeId = employeecontract.getEmployee().getId();
    String grantorSign = employeecontract.getEmployee().getSign();
    if(!authorizedUser.isManager() &&
       !employeeId.equals(authorizedUser.getEmployeeId()) &&
       !authService.isAuthorizedAnyObject(grantorSign, AUTH_CATEGORY_WORKINGDAY, today(), WRITE)) {
      throw new AuthorizationException(WD_READ_REQ_EMPLOYEE_OR_MANAGER);
    }
    return workingdayRepository.findByRefdayAndEmployeecontractId(date, employeecontractId).orElse(null);
  }

  public void upsertWorkingday(Workingday workingday) {
    var employeeId = workingday.getEmployeecontract().getEmployee().getId();
    String grantorSign = workingday.getEmployeecontract().getEmployee().getSign();
    if(!authorizedUser.isManager() &&
       !employeeId.equals(authorizedUser.getEmployeeId()) &&
       !authService.isAuthorizedAnyObject(grantorSign, AUTH_CATEGORY_WORKINGDAY, today(), WRITE)) {
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
    var workingday = workingdayRepository.findById(workingDayId).orElseThrow();

    var employeeId = workingday.getEmployeecontract().getEmployee().getId();
    String grantorSign = workingday.getEmployeecontract().getEmployee().getSign();
    if(!authorizedUser.isManager() &&
       !employeeId.equals(authorizedUser.getEmployeeId()) &&
       !authService.isAuthorizedAnyObject(grantorSign, AUTH_CATEGORY_WORKINGDAY, today(), WRITE)) {
      throw new AuthorizationException(WD_DELETE_REQ_EMPLOYEE_OR_MANAGER);
    }

    workingdayRepository.deleteById(workingDayId);
  }

  public List<Workingday> getWorkingdaysByEmployeeContractId(long employeeContractId, LocalDate dateFirst,
      LocalDate dateLast) {
    var employeecontract = employeecontractService.getEmployeecontractById(employeeContractId);
    var employeeId = employeecontract.getEmployee().getId();
    String grantorSign = employeecontract.getEmployee().getSign();
    if(!authorizedUser.isManager() &&
       !employeeId.equals(authorizedUser.getEmployeeId()) &&
       !authService.isAuthorizedAnyObject(grantorSign, AUTH_CATEGORY_WORKINGDAY, today(), WRITE)) {
      throw new AuthorizationException(WD_READ_REQ_EMPLOYEE_OR_MANAGER);
    }
    return workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, dateFirst, dateLast);
  }

  @EventListener
  void onEmployeecontractDelete(EmployeecontractDeleteEvent event) {
    var workingdays = workingdayRepository.findAllByEmployeecontractId(event.getId());
    workingdayRepository.deleteAll(workingdays);
  }

  @EventListener
  void onEmployeecontractConflictResolution(EmployeecontractConflictResolutionEvent event) {
    var updatingEmployeecontract = event.getUpdatingEmployeecontract();
    var conflictingEmployeecontract = event.getConflictingEmployeecontract();

    var workingdays = workingdayRepository.findAllByEmployeecontractIdAndReferencedayBetween(
        conflictingEmployeecontract.getId(),
        updatingEmployeecontract.getValidFrom(),
        updatingEmployeecontract.getValidUntil()
    );

    workingdays.forEach(wd -> {
      wd.setEmployeecontract(updatingEmployeecontract);
      workingdayRepository.save(wd);
      event.addLog("Informationen zum Arbeitstag am %s nach Vertrag (%s) verschoben".formatted(
          DateUtils.format(wd.getRefday()),
          updatingEmployeecontract.getValidity()
      ));
    });
  }

}
