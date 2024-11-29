package org.tb.employee.auth;

import static org.tb.auth.domain.AccessLevel.LOGIN;
import static org.tb.common.util.DateUtils.today;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.service.AuthService;
import org.tb.employee.domain.Employee;

@Component
@RequiredArgsConstructor
public class EmployeeAuthorization {

  private final AuthService authService;
  private final AuthorizedUser authorizedUser;

  public boolean isAuthorized(Employee employee, AccessLevel accessLevel) {
    if(accessLevel == LOGIN) {
      if(employee.getSign().equals(authorizedUser.getLoginSign())) return true;
      return authService.isAuthorizedAnyObject(employee.getSign(), "EMPLOYEE", today(), LOGIN);
    }

    if(authorizedUser.isManager()) return true;
    if(employee.isNew()) return false; // only managers can access newly created objects (without any id yet)
    if(employee.getSign().equals(authorizedUser.getSign())) return true;
    return false;
  }

}
