package org.tb.dailyreport.auth;

import static org.tb.auth.domain.AccessLevel.DELETE;
import static org.tb.auth.domain.AccessLevel.READ;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static org.tb.common.GlobalConstants.YESNO_YES;
import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.tb.common.exception.ErrorCode.TR_CLOSED_TIME_REPORT_REQ_ADMIN;
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

@Component
@RequiredArgsConstructor
public class TimereportAuthorization {

  private static final String AUTH_CATEGORY_TIMEREPORT = "TIMEREPORT";

  private final AuthorizedUser authorizedUser;
  private final AuthService authService;

  public boolean isAuthorized(Timereport timereport, AccessLevel accessLevel) {
    if(authorizedUser.isManager()) return true;
    if(timereport.getEmployeecontract().getEmployee().getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign())) return true;

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
      if(TIMEREPORT_STATUS_CLOSED.equals(timereport.getStatus()) &&
         !authorizedUser.isAdmin()) {
        return false;
      }
      if(TIMEREPORT_STATUS_COMMITED.equals(timereport.getStatus()) &&
         !authorizedUser.isManager() &&
         !authorizedUser.isAdmin()) {
        return false;
      }
      if(TIMEREPORT_STATUS_COMMITED.equals(timereport.getStatus()) &&
         Objects.equals(authorizedUser.getEffectiveLoginSign(), timereport.getEmployeecontract().getEmployee().getSalatUser().getLoginname())) {
        return false;
      }
      if(TIMEREPORT_STATUS_OPEN.equals(timereport.getStatus()) &&
         !authorizedUser.isAdmin() &&
         !Objects.equals(authorizedUser.getEffectiveLoginSign(), timereport.getEmployeecontract().getEmployee().getSalatUser().getLoginname())) {
        return false;
      }
    }

    var grantor = timereport.getEmployeecontract().getEmployee().getSign();
    var date = timereport.getReferenceday().getRefdate();
    var customerOrderSign = timereport.getSuborder().getCustomerorder().getSign();
    var suborderSign = timereport.getSuborder().getCompleteOrderSign();
    return authService.isAuthorized(grantor, AUTH_CATEGORY_TIMEREPORT, date, accessLevel, customerOrderSign, suborderSign);
  }

  public void checkAuthorized(List<Timereport> timereports, AccessLevel accessLevel) throws AuthorizationException {
    // authorization is based on the status
    timereports.forEach(timereport -> {
      if(accessLevel == DELETE || accessLevel == AccessLevel.WRITE) {
        if(TIMEREPORT_STATUS_CLOSED.equals(timereport.getStatus()) &&
           !authorizedUser.isAdmin()) {
          throw new AuthorizationException(TR_CLOSED_TIME_REPORT_REQ_ADMIN);
        }
        if(TIMEREPORT_STATUS_COMMITED.equals(timereport.getStatus()) &&
           !authorizedUser.isManager() &&
           !authorizedUser.isAdmin()) {
          throw new AuthorizationException(TR_COMMITTED_TIME_REPORT_REQ_MANAGER);
        }
        if(TIMEREPORT_STATUS_COMMITED.equals(timereport.getStatus()) &&
           Objects.equals(authorizedUser.getEffectiveLoginSign(), timereport.getEmployeecontract().getEmployee().getSalatUser().getLoginname())) {
          throw new AuthorizationException(TR_COMMITTED_TIME_REPORT_NOT_SELF);
        }
        if(TIMEREPORT_STATUS_OPEN.equals(timereport.getStatus()) &&
           !authorizedUser.isAdmin() &&
           !Objects.equals(authorizedUser.getEffectiveLoginSign(), timereport.getEmployeecontract().getEmployee().getSalatUser().getLoginname())) {
          throw new AuthorizationException(TR_OPEN_TIME_REPORT_REQ_EMPLOYEE);
        }
      }
      if(!isAuthorized(timereport, accessLevel)) {
        throw new AuthorizationException(AA_NOT_ATHORIZED);
      }
    });
  }

}
