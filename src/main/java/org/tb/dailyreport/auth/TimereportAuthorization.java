package org.tb.dailyreport.auth;

import static org.tb.auth.domain.AccessLevel.READ;
import static org.tb.common.GlobalConstants.YESNO_YES;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.dailyreport.domain.Timereport;

@Component
@RequiredArgsConstructor
public class TimereportAuthorization {

  private static final String AUTH_CATEGORY_TIMEREPORT = "TIMEREPORT";

  private final AuthorizedUser authorizedUser;
  private final AuthService authService;

  public boolean isAuthorized(Timereport timereport, AccessLevel accessLevel) {
    if(authorizedUser.isManager()) return true;
    if(timereport.getEmployeecontract().getEmployee().getSign().equals(authorizedUser.getSign())) return true;

    if(accessLevel == READ) {
      // every project manager may see the time reports of her project
      if(authorizedUser.getSign().equals(timereport.getSuborder().getCustomerorder().getResponsible_hbt().getSign())) {
        return true;
      }
      if(authorizedUser.getSign().equals(timereport.getSuborder().getCustomerorder().getRespEmpHbtContract().getSign())) {
        return true;
      }

      // backoffice authorizedUsers may see time reports that must be invoiced
      if(authorizedUser.isBackoffice() && timereport.getSuborder().getInvoice() == YESNO_YES) {
        return true;
      }
    }

    var grantor = timereport.getEmployeecontract().getEmployee().getSign();
    var date = timereport.getReferenceday().getRefdate();
    var customerOrderSign = timereport.getSuborder().getCustomerorder().getSign();
    var suborderSign = timereport.getSuborder().getCompleteOrderSign();
    return authService.isAuthorized(grantor, AUTH_CATEGORY_TIMEREPORT, date, accessLevel, customerOrderSign, suborderSign);
  }

}
