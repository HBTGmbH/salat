package de.hbt.salat.employee.listener;

import static de.hbt.salat.common.exception.ErrorCode.EM_NO_LOGIN_EMPLOYEE;
import static de.hbt.salat.common.exception.ServiceFeedbackMessage.error;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.event.AuthorizedUserChangedEvent;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.EmployeeAccessDenial;
import de.hbt.salat.employee.event.EmployeecontractChangedEvent;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;

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
   * Hält fest, dass dieser Anmeldename die Anwendung nicht benutzen kann, weil kein Mitarbeiter zu ihm
   * gehört, und antwortet nicht selbst: die Filterketten sind zustandslos, der Ereignishörer läuft
   * deshalb bei jeder Anfrage und auch in der Weiterleitung auf {@code /error}. Eine hier geworfene Ausnahme nahm die Fehlerseite mit
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

    // Ohne heute gültigen Vertrag geht die Anmeldung trotzdem durch: die Rolle hängt am Login, und
    // Seiten zum eigenen Vertrag zeigen dann einen Hinweis (#1559, #1588).
    employeecontractService.getCurrentContract(loginEmployee.getId()).ifPresent(contract ->
        eventPublisher.publishEvent(new EmployeecontractChangedEvent(this, contract.getId())));

  }

}
