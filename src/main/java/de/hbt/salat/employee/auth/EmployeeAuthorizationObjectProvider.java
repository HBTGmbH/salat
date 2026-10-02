package de.hbt.salat.employee.auth;

import java.util.List;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AuthorizationObject;
import de.hbt.salat.auth.domain.AuthorizationObjectProvider;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.viewhelper.EmployeeLabelViewHelper;

/**
 * What the rule editor may offer for the category {@code EMPLOYEE} (#1074).
 *
 * <p>The id is the <em>login name</em>, not the sign — that is what {@link EmployeeAuthorization} passes. The two are
 * usually the same string and are not the same thing, which is why the hint at the field says so: a rule written with
 * the sign where the login name belongs looks right and never fires.
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
    return byLoginname(employeeService.getSelectableEmployees(null));
  }

  /**
   * One entry per login name, in the order of the login names, named like the person in every other select (#1266).
   * The grantees of a rule are logins as well ({@link EmployeeGranteeProvider}).
   */
  static List<AuthorizationObject> byLoginname(List<Employee> employees) {
    var byLoginname = new TreeMap<String, AuthorizationObject>();
    employees.stream()
        .filter(employee -> employee.getLoginname() != null && !employee.getLoginname().isBlank())
        .forEach(employee -> byLoginname.putIfAbsent(employee.getLoginname(), new AuthorizationObject(
            employee.getLoginname(), EmployeeLabelViewHelper.of(employee.getName(), employee.getSign()),
            employee.getSign())));
    return List.copyOf(byLoginname.values());
  }

}
