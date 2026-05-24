package org.tb.order.auth;

import static org.tb.auth.domain.AccessLevel.READ;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Employeeorder;

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
    return ec.getSupervisor() != null &&
        ec.getSupervisor().getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign());
  }

}
