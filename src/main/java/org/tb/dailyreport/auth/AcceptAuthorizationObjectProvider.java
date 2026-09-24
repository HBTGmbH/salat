package org.tb.dailyreport.auth;

import org.springframework.stereotype.Component;
import org.tb.employee.service.EmployeeService;

/** What the rule editor may offer for the category {@code ACCEPT_TIMEREPORTS} (#1074). */
@Component
public class AcceptAuthorizationObjectProvider extends EmployeeSignAuthorizationObjectProvider {

  public AcceptAuthorizationObjectProvider(EmployeeService employeeService) {
    super(employeeService);
  }

  @Override
  public String category() {
    return "ACCEPT_TIMEREPORTS";
  }

  @Override
  public String labelKey() {
    return "main.auth.rule.category.accept";
  }

}
