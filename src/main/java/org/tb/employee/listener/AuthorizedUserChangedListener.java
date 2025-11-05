package org.tb.employee.listener;

import static java.lang.Boolean.TRUE;
import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.event.AuthorizedUserChangedEvent;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employee;
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
  private final HttpServletRequest request;

  @EventListener
  public void onAuthorizedUserChanged(AuthorizedUserChangedEvent event) {

    Employee loginEmployee = employeeService.getLoginEmployee();

    if (loginEmployee == null) {
      log.error("No matching employee found for {}.", authorizedUser.getSign());
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No matching employee found for " + authorizedUser.getSign());
    }

    LocalDate today = today();
    Optional<Employeecontract> employeecontract = employeecontractService.getCurrentContract(loginEmployee.getId());
    if (employeecontract.isEmpty() && !loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
      log.error("No valid contract found for {}.", loginEmployee.getSign());
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No valid contract found for " + loginEmployee.getSign());
    }

    authorizedUser.init(loginEmployee.getId(), loginEmployee.isRestricted(), loginEmployee.getStatus());

    // no further stuff for REST API calls - all is just for struts and old web UI
    if(request.getRequestURL().toString().contains("/api/") || request.getRequestURL().toString().contains("/rest/")) return;

    request.getSession().setAttribute("loginEmployee", loginEmployee);
    String loginEmployeeFullName = loginEmployee.getFirstname() + " " + loginEmployee.getLastname();
    request.getSession().setAttribute("loginEmployeeFullName", loginEmployeeFullName);
    request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());

    // check if employee has an employee contract and it has employee orders for all standard suborders
    if (employeecontract.isPresent()) {
      request.getSession().setAttribute("employeeHasValidContract", true);
      handleEmployeeWithValidContract(request, employeecontract.get());
    } else {
      request.getSession().setAttribute("employeeHasValidContract", false);
    }

    // create collection of employeecontracts
    List<Employeecontract> employeecontracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today);
    request.getSession().setAttribute("employeecontracts", employeecontracts);
  }

  private void handleEmployeeWithValidContract(HttpServletRequest request, Employeecontract employeecontract) {
    // set used employee contract of login employee
    request.getSession().setAttribute("loginEmployeeContract", employeecontract);
    request.getSession().setAttribute("loginEmployeeContractId", employeecontract.getId());
    request.getSession().setAttribute("currentEmployeeContract", employeecontract);

    eventPublisher.publishEvent(new EmployeecontractChangedEvent(this, employeecontract.getId()));
  }

}
