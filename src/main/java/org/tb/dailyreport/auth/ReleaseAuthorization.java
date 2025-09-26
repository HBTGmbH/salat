package org.tb.dailyreport.auth;

import static java.time.LocalDate.now;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.employee.domain.Employeecontract;

@Component
@RequiredArgsConstructor
public class ReleaseAuthorization {

  private static final String AUTH_CATEGORY_RELEASE = "RELEASE_TIMEREPORTS";
  private static final String AUTH_CATEGORY_ACCEPT = "ACCEPT_TIMEREPORTS";

  private final AuthorizedUser authorizedUser;
  private final AuthService authService;

  public boolean isReleaseAuthorized(Employeecontract employeecontract, AccessLevel accessLevel) {
    if(authorizedUser.getSign().equals(employeecontract.getEmployee().getSign())) return true;
    if (authorizedUser.isManager()) return true;
    if (authorizedUser.isAdmin()) return true;
    String grantorSign = employeecontract.getEmployee().getSign();
    return authService.isAuthorizedAnyObject(grantorSign, AUTH_CATEGORY_RELEASE, now(), accessLevel);
  }

  public boolean isAcceptAuthorized(Employeecontract employeecontract, AccessLevel accessLevel) {
    if(authorizedUser.getSign().equals(employeecontract.getEmployee().getSign())) return false; // cannot accept own hours
    if (authorizedUser.isManager()) return true;
    if (authorizedUser.isAdmin()) return true;
    String grantorSign = employeecontract.getEmployee().getSign();
    return authService.isAuthorizedAnyObject(grantorSign, AUTH_CATEGORY_ACCEPT, now(), accessLevel);
  }

}
