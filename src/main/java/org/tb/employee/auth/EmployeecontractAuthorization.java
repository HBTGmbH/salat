package org.tb.employee.auth;

import static org.tb.auth.domain.AccessLevel.READ;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.employee.domain.Employeecontract;

@Component
@RequiredArgsConstructor
public class EmployeecontractAuthorization {

  private final AuthorizedUser authorizedUser;

  public boolean isAuthorized(Employeecontract ec, AccessLevel accessLevel) {
    if (authorizedUser.isManager()) return true;
    if (accessLevel == READ && ec.getEmployee().getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign())) return true;
    if (accessLevel == READ && authorizedUser.isPeopleLead() && isSupervisedByCurrentUser(ec)) return true;
    return false;
  }

  private boolean isSupervisedByCurrentUser(Employeecontract ec) {
    return ec.getSupervisor() != null &&
        ec.getSupervisor().getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign());
  }

}
