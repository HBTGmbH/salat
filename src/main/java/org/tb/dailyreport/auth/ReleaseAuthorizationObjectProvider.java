package org.tb.dailyreport.auth;

import org.springframework.stereotype.Component;
import org.tb.employee.service.EmployeeService;

/** What the rule editor may offer for the category {@code RELEASE_TIMEREPORTS} (#1074). */
@Component
public class ReleaseAuthorizationObjectProvider extends EmployeeSignAuthorizationObjectProvider {

  public ReleaseAuthorizationObjectProvider(EmployeeService employeeService) {
    super(employeeService);
  }

  @Override
  public String category() {
    return "RELEASE_TIMEREPORTS";
  }

  @Override
  public String labelKey() {
    return "main.auth.rule.category.release";
  }

}
