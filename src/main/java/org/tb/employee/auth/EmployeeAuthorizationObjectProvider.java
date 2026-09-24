package org.tb.employee.auth;

import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizationObject;
import org.tb.auth.domain.AuthorizationObjectProvider;
import org.tb.employee.service.EmployeeService;

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
    return employeeService.getSelectableEmployees(null).stream()
        .map(employee -> employee.getLoginname())
        .filter(loginname -> loginname != null && !loginname.isBlank())
        .distinct()
        .sorted(Comparator.naturalOrder())
        .map(loginname -> new AuthorizationObject(loginname, loginname))
        .toList();
  }

}
