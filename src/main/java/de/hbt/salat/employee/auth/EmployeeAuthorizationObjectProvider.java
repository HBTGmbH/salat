package de.hbt.salat.employee.auth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AuthorizationObject;
import de.hbt.salat.auth.domain.AuthorizationObjectProvider;
import de.hbt.salat.auth.domain.ObjectJudgement;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.viewhelper.EmployeeLabelViewHelper;

/**
 * What the rule editor may offer for the category {@code EMPLOYEE} (#1074).
 *
 * <p>The object is the <em>login</em> that may be taken over, by the id of its {@code SalatUser} — that is what
 * {@link EmployeeAuthorization} and {@code AuthService#switchLogin} pass. Not the login name and not the sign (#1204):
 * both can be changed or anonymized, and whoever got the old one next would be the one the rule names.
 *
 * <p>Offered are the people not hidden; what a rule already carries is added back by the editor, so hiding
 * somebody never makes an existing rule uneditable.
 *
 * <p>A rule of this category with {@code LOGIN} lets the grantee act in the named person's name.
 */
@Component
@RequiredArgsConstructor
public class EmployeeAuthorizationObjectProvider implements AuthorizationObjectProvider {

  private final EmployeeService employeeService;

  @Override
  public String category() {
    return "EMPLOYEE";
  }

  @Override
  public String labelKey() {
    return "main.auth.rule.category.employee";
  }

  @Override
  public String objectHintKey() {
    return "main.auth.rule.object.hint.employee";
  }

  @Override
  public List<AuthorizationObject> objects() {
    return byLogin(employeeService.getSelectableEmployees(null));
  }

  @Override
  public Map<String, AuthorizationObject> describe(Collection<String> objectIds) {
    return describeLogins(employeeService, objectIds);
  }

  @Override
  public ObjectJudgement judge(String objectId) {
    if (!isId(objectId)) {
      return ObjectJudgement.MALFORMED;
    }
    return AuthorizationObjectProvider.super.judge(objectId);
  }

  /**
   * One entry per login, in the order of the login names, named like the person in every other select (#1266) and
   * showing the login name once picked. The grantees of a rule are logins as well ({@link EmployeeGranteeProvider}).
   */
  static List<AuthorizationObject> byLogin(List<Employee> employees) {
    var byLoginname = new TreeMap<String, AuthorizationObject>();
    employees.stream()
        .filter(employee -> employee.getSalatUser() != null && employee.getSalatUser().getId() != null)
        .filter(employee -> employee.getLoginname() != null && !employee.getLoginname().isBlank())
        .forEach(employee -> byLoginname.putIfAbsent(employee.getLoginname(), loginOf(employee)));
    return List.copyOf(byLoginname.values());
  }

  /** The logins with these ids, hidden people included. */
  static Map<String, AuthorizationObject> describeLogins(EmployeeService employeeService, Collection<String> ids) {
    var salatUserIds = ids.stream().filter(EmployeeAuthorizationObjectProvider::isId).map(Long::valueOf).toList();
    var described = new TreeMap<String, AuthorizationObject>();
    employeeService.getEmployeesBySalatUserIds(salatUserIds)
        .forEach(employee -> described.putIfAbsent(String.valueOf(employee.getSalatUser().getId()), loginOf(employee)));
    return described;
  }

  static boolean isId(String value) {
    return !value.isEmpty() && value.chars().allMatch(Character::isDigit);
  }

  private static AuthorizationObject loginOf(Employee employee) {
    return new AuthorizationObject(String.valueOf(employee.getSalatUser().getId()),
        EmployeeLabelViewHelper.of(employee.getName(), employee.getSign()), employee.getLoginname());
  }

}
