package de.hbt.salat.employee.auth;

import static de.hbt.salat.auth.domain.AccessLevel.READ;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.employee.domain.Employeecontract;

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
    return ec.getSupervisors().stream()
        .anyMatch(s -> s.getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign()));
  }

}
