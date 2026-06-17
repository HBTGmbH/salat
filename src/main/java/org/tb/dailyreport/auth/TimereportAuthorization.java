package org.tb.dailyreport.auth;

import static org.tb.auth.domain.AccessLevel.DELETE;
import static org.tb.auth.domain.AccessLevel.READ;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static org.tb.common.GlobalConstants.YESNO_YES;
import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.tb.common.exception.ErrorCode.TR_CLOSED_TIME_REPORT_REQ_MANAGER;
import static org.tb.common.exception.ErrorCode.TR_COMMITTED_TIME_REPORT_NOT_SELF;
import static org.tb.common.exception.ErrorCode.TR_COMMITTED_TIME_REPORT_REQ_MANAGER;
import static org.tb.common.exception.ErrorCode.TR_OPEN_TIME_REPORT_REQ_EMPLOYEE;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.common.exception.AuthorizationException;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employeecontract;

@Component
@RequiredArgsConstructor
public class TimereportAuthorization {

  private static final String AUTH_CATEGORY_TIMEREPORT = "TIMEREPORT";

  private final AuthorizedUser authorizedUser;
  private final AuthService authService;

  public boolean isAuthorized(Timereport timereport, AccessLevel accessLevel) {
    if(accessLevel == READ && authorizedUser.isManager()) return true;
    if(accessLevel == READ && authorizedUser.isPeopleLead() && isSupervisedByCurrentUser(timereport.getEmployeecontract())) return true;
    var isOwner = timereport.getEmployeecontract().getEmployee().getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign());
    if(isOwner && accessLevel == READ) return true;

    if(accessLevel == READ) {
      // every project manager may see the time reports of her project
      if(authorizedUser.getEffectiveLoginSign().equals(timereport.getSuborder().getCustomerorder().getResponsible_hbt().getSalatUser().getLoginname())) {
        return true;
      }
      if(authorizedUser.getEffectiveLoginSign().equals(timereport.getSuborder().getCustomerorder().getRespEmpHbtContract().getSalatUser().getLoginname())) {
        return true;
      }

      // backoffice authorizedUsers may see time reports that must be invoiced
      if(authorizedUser.isBackoffice() && timereport.getSuborder().getInvoice() == YESNO_YES) {
        return true;
      }
    }

    if(accessLevel == DELETE || accessLevel == AccessLevel.WRITE) {
      // write allowance depends on timereport status
      if(TIMEREPORT_STATUS_CLOSED.equals(timereport.getStatus())) {
        return authorizedUser.isManager() && !isOwner;
      }
      if(TIMEREPORT_STATUS_COMMITED.equals(timereport.getStatus())) {
        return !isOwner && (
          authorizedUser.isManager() || (authorizedUser.isPeopleLead() && isSupervisedByCurrentUser(timereport.getEmployeecontract()))
        );
      }
      // TIMEREPORT_STATUS_OPEN timereports may be written by manager and owners
      if(TIMEREPORT_STATUS_OPEN.equals(timereport.getStatus()) && (authorizedUser.isManager() || isOwner)) {
        return true;
      }
    }

    // check rules as fallback
    var grantor = timereport.getEmployeecontract().getEmployee().getSign();
    var date = timereport.getReferenceday().getRefdate();
    var customerOrderSign = timereport.getSuborder().getCustomerorder().getSign();
    var suborderSign = timereport.getSuborder().getCompleteOrderSign();
    return authService.isAuthorized(grantor, AUTH_CATEGORY_TIMEREPORT, date, accessLevel, customerOrderSign, suborderSign);
  }

  public void checkAuthorized(List<Timereport> timereports, AccessLevel accessLevel) throws AuthorizationException {
    // authorization is based on the status
    timereports.forEach(timereport -> {

      // pre qualify write access rules
      if(accessLevel == DELETE || accessLevel == AccessLevel.WRITE) {
        var isOwner = Objects.equals(authorizedUser.getEffectiveLoginSign(), timereport.getEmployeecontract().getEmployee().getSalatUser().getLoginname());
        if(TIMEREPORT_STATUS_CLOSED.equals(timereport.getStatus()) &&
           (!authorizedUser.isManager() || isOwner)) {
          throw new AuthorizationException(TR_CLOSED_TIME_REPORT_REQ_MANAGER);
        }
        if(TIMEREPORT_STATUS_COMMITED.equals(timereport.getStatus()) &&
           !authorizedUser.isManager() &&
           !(authorizedUser.isPeopleLead() && isSupervisedByCurrentUser(timereport.getEmployeecontract()))) {
          throw new AuthorizationException(TR_COMMITTED_TIME_REPORT_REQ_MANAGER);
        }
        if(TIMEREPORT_STATUS_COMMITED.equals(timereport.getStatus()) && isOwner) {
          throw new AuthorizationException(TR_COMMITTED_TIME_REPORT_NOT_SELF);
        }
        if(TIMEREPORT_STATUS_OPEN.equals(timereport.getStatus()) &&
           !authorizedUser.isManager() &&
           !isOwner) {
          throw new AuthorizationException(TR_OPEN_TIME_REPORT_REQ_EMPLOYEE);
        }
      }

      // ensure isAuthorized checks are made
      if(!isAuthorized(timereport, accessLevel)) {
        throw new AuthorizationException(AA_NOT_ATHORIZED);
      }
    });
  }

  private boolean isSupervisedByCurrentUser(Employeecontract ec) {
    return ec.getSupervisors().stream()
        .anyMatch(s -> s.getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign()));
  }

}
