package de.hbt.salat.dailyreport.auth;

import java.util.List;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import de.hbt.salat.auth.domain.AuthorizationObject;
import de.hbt.salat.auth.domain.AuthorizationObjectProvider;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.viewhelper.EmployeeLabelViewHelper;

/**
 * Shared by the three categories of this module whose object is the person whose records are at stake (#1074):
 * releases, acceptances and working days. Since #1089 that person stands in the object of the rule.
 *
 * <p>The id is the employee <em>sign</em>, because that is what the calling sites pass — unlike {@code EMPLOYEE},
 * which passes the login name.
 *
 * <p>Offered are the people not hidden: {@code getSelectableEmployees} is the method for what belongs in a select box,
 * and {@code getAllEmployeeSigns} is deliberately not — that one answers whether a stored sign resolves and says so.
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
    // one entry per sign, in the order of the signs, named like the person in every other select (#1266)
    var bySign = new TreeMap<String, AuthorizationObject>();
    employeeService.getSelectableEmployees(null).stream()
        .filter(employee -> employee.getSign() != null && !employee.getSign().isBlank())
        .forEach(employee -> bySign.putIfAbsent(employee.getSign(),
            new AuthorizationObject(employee.getSign(), EmployeeLabelViewHelper.of(employee.getName(), employee.getSign()),
                employee.getSign())));
    return List.copyOf(bySign.values());
  }

}
