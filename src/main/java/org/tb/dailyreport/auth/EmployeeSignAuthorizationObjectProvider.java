package org.tb.dailyreport.auth;

import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.tb.auth.domain.AuthorizationObject;
import org.tb.auth.domain.AuthorizationObjectProvider;
import org.tb.employee.service.EmployeeService;

/**
 * Shared by the three categories of this module whose object is the person whose records are at stake (#1074):
 * releases, acceptances and working days. Since #1089 that person stands in the object of the rule.
 *
 * <p>The id is the employee <em>sign</em>, because that is what the calling sites pass — unlike {@code EMPLOYEE},
 * which passes the login name.
 */
@RequiredArgsConstructor
abstract class EmployeeSignAuthorizationObjectProvider implements AuthorizationObjectProvider {

  private final EmployeeService employeeService;

  @Override
  public String objectHintKey() {
    return "main.auth.rule.object.hint.employeesign";
  }

  @Override
  public List<AuthorizationObject> objects() {
    return employeeService.getAllEmployeeSigns().stream()
        .filter(sign -> sign != null && !sign.isBlank())
        .sorted(Comparator.naturalOrder())
        .map(sign -> new AuthorizationObject(sign, sign))
        .toList();
  }

}
