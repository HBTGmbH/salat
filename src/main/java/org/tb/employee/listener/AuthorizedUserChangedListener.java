package org.tb.employee.listener;

import static org.tb.common.util.DateUtils.today;

import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.event.AuthorizedUserChangedEvent;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizedUserChangedListener {

  private final AuthorizedUser authorizedUser;
  private final AuthorizedEmployee authorizedEmployee;
  private final EmployeeService employeeService;
  private final EmployeecontractService employeecontractService;

  @EventListener
  public void onAuthorizedUserChanged(AuthorizedUserChangedEvent event) {

    Employee loginEmployee = employeeService.getLoginEmployee();

    if (loginEmployee == null) {
      log.error("No matching employee found for {}.", authorizedUser.getEffectiveLoginSign());
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No matching employee found for " + authorizedUser.getEffectiveLoginSign());
    }

    LocalDate today = today();
    Optional<Employeecontract> employeecontract = employeecontractService.getCurrentContract(loginEmployee.getId());
    if (employeecontract.isEmpty() && !loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
      log.error("No valid contract found for {}.", loginEmployee.getSign());
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No valid contract found for " + loginEmployee.getSign());
    }

    authorizedEmployee.login(loginEmployee);
  }

}
