package de.hbt.salat.employee.auth;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AuthorizationGranteeProvider;
import de.hbt.salat.auth.domain.AuthorizationObject;
import de.hbt.salat.employee.service.EmployeeService;

/**
 * The logins the rule editor offers as grantees (#1074) — everyone not hidden.
 *
 * <p>{@code getSelectableEmployees} is the method for exactly this: what belongs in a select box. Hiding an employee
 * is a decluttering aid for these lists, and this is one of them.
 */
@Component
@RequiredArgsConstructor
public class EmployeeGranteeProvider implements AuthorizationGranteeProvider {

  private final EmployeeService employeeService;

  @Override
  public List<AuthorizationObject> granteeCandidates() {
    return EmployeeAuthorizationObjectProvider.byLoginname(employeeService.getSelectableEmployees(null));
  }

}
