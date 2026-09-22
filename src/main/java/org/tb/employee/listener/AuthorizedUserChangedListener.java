package org.tb.employee.listener;

import static org.tb.common.exception.ErrorCode.EC_NO_CURRENT_CONTRACT;
import static org.tb.common.exception.ErrorCode.EM_NO_LOGIN_EMPLOYEE;
import static org.tb.common.exception.ServiceFeedbackMessage.error;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.event.AuthorizedUserChangedEvent;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.EmployeeAccessDenial;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.event.EmployeecontractChangedEvent;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizedUserChangedListener {

  private final ApplicationEventPublisher eventPublisher;
  private final AuthorizedUser authorizedUser;
  private final EmployeeService employeeService;
  private final EmployeecontractService employeecontractService;
  private final EmployeeAccessDenial employeeAccessDenial;

  /**
   * Hält fest, dass dieser Anmeldename die Anwendung nicht benutzen kann, und antwortet nicht selbst:
   * die Filterketten sind zustandslos, der Ereignishörer läuft deshalb bei jeder Anfrage und auch in
   * der Weiterleitung auf {@code /error}. Eine hier geworfene Ausnahme nahm die Fehlerseite mit
   * (#1054). Die Antwort gibt {@code EmployeeAccessFilter}.
   */
  @EventListener
  public void onAuthorizedUserChanged(AuthorizedUserChangedEvent event) {

    Employee loginEmployee = employeeService.getLoginEmployee();

    if (loginEmployee == null) {
      log.warn("No matching employee found for {}.", authorizedUser.getEffectiveLoginSign());
      employeeAccessDenial.deny(error(EM_NO_LOGIN_EMPLOYEE, authorizedUser.getEffectiveLoginSign()));
      return;
    }

    Optional<Employeecontract> employeecontract = employeecontractService.getCurrentContract(loginEmployee.getId());
    if (employeecontract.isEmpty() && !loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
      log.warn("No valid contract found for {}.", loginEmployee.getSign());
      employeeAccessDenial.deny(error(EC_NO_CURRENT_CONTRACT, loginEmployee.getSign()));
      return;
    }

    employeecontract.ifPresent(contract ->
        eventPublisher.publishEvent(new EmployeecontractChangedEvent(this, contract.getId())));

  }

}
