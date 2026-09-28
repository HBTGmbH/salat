package de.hbt.salat.order.auth;

import static de.hbt.salat.auth.domain.AccessLevel.READ;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.order.domain.Employeeorder;

@Component
@RequiredArgsConstructor
public class EmployeeorderAuthorization {

  private final AuthorizedUser authorizedUser;

  public boolean isAuthorized(Employeeorder employeeorder, AccessLevel accessLevel) {
    if (authorizedUser.isManager()) return true;
    if (accessLevel == READ && authorizedUser.isPeopleLead() && isSupervisedByCurrentUser(employeeorder.getEmployeecontract())) return true;
    return employeeorder.getEmployeecontract().getEmployee().getSalatUser().getLoginname()
        .equals(authorizedUser.getEffectiveLoginSign());
  }

  private boolean isSupervisedByCurrentUser(Employeecontract ec) {
    return ec.getSupervisors().stream()
        .anyMatch(s -> s.getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign()));
  }

}
