package de.hbt.salat.employee.auth;

import static de.hbt.salat.auth.domain.AccessLevel.LOGIN;
import static de.hbt.salat.auth.domain.AccessLevel.READ;
import static de.hbt.salat.common.util.DateUtils.today;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.employee.domain.Employee;

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
      return authService.isAuthorizedForOwnLogin(AUTH_CATEGORY_EMPLOYEE, today(), LOGIN, employee.getSalatUser().getLoginname());
    }

    if (authorizedUser.isManager()) return true;
    if (employee.isNew()) return false;
    if (accessLevel == READ && employee.getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign())) return true;
    if (accessLevel == READ && authorizedUser.isPeopleLead() && supervisedEmployeeIds.contains(employee.getId())) return true;
    return false;
  }

}
