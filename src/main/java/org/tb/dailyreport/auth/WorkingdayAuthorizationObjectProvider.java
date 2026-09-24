package org.tb.dailyreport.auth;

import org.springframework.stereotype.Component;
import org.tb.employee.service.EmployeeService;

/** What the rule editor may offer for the category {@code WORKINGDAY} (#1074). */
@Component
public class WorkingdayAuthorizationObjectProvider extends EmployeeSignAuthorizationObjectProvider {

  public WorkingdayAuthorizationObjectProvider(EmployeeService employeeService) {
    super(employeeService);
  }

  @Override
  public String category() {
    return "WORKINGDAY";
  }

  @Override
  public String labelKey() {
    return "main.auth.rule.category.workingday";
  }

}
