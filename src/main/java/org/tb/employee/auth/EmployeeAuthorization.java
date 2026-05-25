package org.tb.employee.auth;

import static org.tb.auth.domain.AccessLevel.LOGIN;
import static org.tb.auth.domain.AccessLevel.READ;
import static org.tb.common.util.DateUtils.today;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.employee.domain.Employee;

@Component
@RequiredArgsConstructor
public class EmployeeAuthorization {

  private static final String AUTH_CATEGORY_EMPLOYEE = "EMPLOYEE";

  private final AuthService authService;
  private final AuthorizedUser authorizedUser;

  public boolean isAuthorized(Employee employee, AccessLevel accessLevel) {
    return isAuthorized(employee, accessLevel, Set.of());
  }

  public boolean isAuthorized(Employee employee, AccessLevel accessLevel, Set<Long> supervisedEmployeeIds) {
    if (accessLevel == LOGIN) {
      if (employee.getSalatUser().getLoginname().equals(authorizedUser.getLoginSign())) return true;
      return authService.isAuthorizedAnyObject(employee.getSalatUser().getLoginname(), AUTH_CATEGORY_EMPLOYEE, today(), LOGIN, true);
    }

    if (authorizedUser.isManager()) return true;
    if (employee.isNew()) return false;
    if (accessLevel == READ && employee.getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign())) return true;
    if (accessLevel == READ && authorizedUser.isPeopleLead() && supervisedEmployeeIds.contains(employee.getId())) return true;
    return false;
  }

}
