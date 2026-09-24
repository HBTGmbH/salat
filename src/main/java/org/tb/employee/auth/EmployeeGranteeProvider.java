package org.tb.employee.auth;

import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizationGranteeProvider;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;

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
  public List<String> granteeCandidates() {
    return employeeService.getSelectableEmployees(null).stream()
        .map(Employee::getLoginname)
        .filter(loginname -> loginname != null && !loginname.isBlank())
        .distinct()
        .sorted(Comparator.naturalOrder())
        .toList();
  }

}
