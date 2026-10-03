package de.hbt.salat.dailyreport.auth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import de.hbt.salat.auth.domain.AuthorizationObject;
import de.hbt.salat.auth.domain.AuthorizationObjectProvider;
import de.hbt.salat.auth.domain.ObjectJudgement;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.viewhelper.EmployeeLabelViewHelper;

/**
 * Shared by the three categories of this module whose object is the person whose records are at stake (#1074):
 * releases, acceptances and working days. Since #1089 that person stands in the object of the rule.
 *
 * <p>The id is the <em>employee id</em>, because that is what the calling sites pass (#1204) — not the sign, which can
 * be changed or anonymized and then be given to somebody else, who would inherit the rule. Unlike {@code EMPLOYEE},
 * whose object is a login.
 *
 * <p>Offered are the people not hidden: {@code getSelectableEmployees} is the method for what belongs in a select box.
 * What a rule already carries is described with hidden people included, so hiding somebody never makes a rule
 * unreadable.
 */
@RequiredArgsConstructor
abstract class EmployeeIdAuthorizationObjectProvider implements AuthorizationObjectProvider {

  private final EmployeeService employeeService;

  @Override
  public String objectHintKey() {
    return "main.auth.rule.object.hint.employeeid";
  }

  @Override
  public List<AuthorizationObject> objects() {
    // one entry per person, in the order of the signs, named like the person in every other select (#1266)
    var bySign = new TreeMap<String, AuthorizationObject>();
    employeeService.getSelectableEmployees(null).stream()
        .filter(employee -> employee.getSign() != null && !employee.getSign().isBlank())
        .forEach(employee -> bySign.putIfAbsent(employee.getSign(), personOf(employee)));
    return List.copyOf(bySign.values());
  }

  @Override
  public Map<String, AuthorizationObject> describe(Collection<String> objectIds) {
    var ids = objectIds.stream().filter(EmployeeIdAuthorizationObjectProvider::isId).map(Long::valueOf).toList();
    var described = new TreeMap<String, AuthorizationObject>();
    if (!ids.isEmpty()) {
      employeeService.getEmployeesByIds(ids).forEach(employee -> described.put(String.valueOf(employee.getId()),
          personOf(employee)));
    }
    return described;
  }

  @Override
  public ObjectJudgement judge(String objectId) {
    if (!isId(objectId)) {
      return ObjectJudgement.MALFORMED;
    }
    return AuthorizationObjectProvider.super.judge(objectId);
  }

  private static boolean isId(String value) {
    return !value.isEmpty() && value.chars().allMatch(Character::isDigit);
  }

  private static AuthorizationObject personOf(Employee employee) {
    return new AuthorizationObject(String.valueOf(employee.getId()),
        EmployeeLabelViewHelper.of(employee.getName(), employee.getSign()), employee.getSign());
  }

}
