package org.tb.employee.service;

import static org.tb.auth.domain.AccessLevel.LOGIN;
import static org.tb.common.exception.ErrorCode.AA_REQUIRED;
import static org.tb.common.exception.ErrorCode.EM_DELETE_GOT_VETO;
import static org.tb.common.exception.ServiceFeedbackMessage.error;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.exception.VetoedException;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.SalatUser;
import org.tb.employee.event.EmployeeDeleteEvent;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.employee.persistence.SalatUserRepository;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class EmployeeService {

  private final ApplicationEventPublisher eventPublisher;
  private final EmployeeRepository employeeRepository;
  private final SalatUserRepository salatUserRepository;
  private final EmployeeDAO employeeDAO;
  private final AuthorizedUser authorizedUser;
  private final EmployeeAuthorization employeeAuthorization;

  public Employee getLoginEmployee() {
    var loginEmployee = employeeDAO.getLoginEmployee(authorizedUser.getSign()); // do not use loginSign!!!
    var allowedLoginEmployees = getLoginEmployees();
    if(!allowedLoginEmployees.contains(loginEmployee)) {
      log.warn("User {} tried to impersonate {}. This was not authorized!", authorizedUser.getLoginSign(), authorizedUser.getSign());
      throw new AuthorizationException(AA_REQUIRED);
    }
    return loginEmployee;
  }

  public List<Employee> getLoginEmployees() {
    var employees = StreamSupport
        .stream(employeeRepository.findAll().spliterator(), false).toList();
    return employees.stream()
        .filter(e -> employeeAuthorization.isAuthorized(e, LOGIN))
        .toList();
  }

  public List<Employee> getAllEmployees() {
    return employeeDAO.getEmployees();
  }

  public List<Employee> getEmployeesWithContracts() {
    return employeeDAO.getEmployeesWithContracts();
  }

  public Employee getEmployeeBySign(String sign) {
    return employeeDAO.getEmployeeBySign(sign);
  }

  public Employee getEmployeeById(long employeeId) {
    return employeeDAO.getEmployeeById(employeeId);
  }

  public List<Employee> getEmployeesWithValidContracts() {
    return employeeDAO.getEmployeesWithValidContracts();
  }

  public List<Employee> getEmployeesByFilter(String filter) {
    return employeeDAO.getEmployeesByFilter(filter);
  }

  @Authorized(requiresManager = true)
  public void deleteEmployeeById(long employeeId) {
    var employee = employeeDAO.getEmployeeById(employeeId);

    if(!employeeAuthorization.isAuthorized(employee, AccessLevel.DELETE)) {
      throw new RuntimeException("Illegal access to delete " + employeeId + " by " + authorizedUser.getEmployeeId());
    }

    if(employee.isNew()) {
      var event = new EmployeeDeleteEvent(employee.getId());
      try {
        eventPublisher.publishEvent(event);
      } catch(VetoedException e) {
        // adding context to the veto to make it easier to understand the complete picture
        var allMessages = new ArrayList<ServiceFeedbackMessage>();
        allMessages.add(error(
            EM_DELETE_GOT_VETO,
            employee.getSign()
        ));
        allMessages.addAll(e.getMessages());
        event.veto(allMessages);
      }
    }
    
    // Delete the employee first (this will remove the join table entry due to cascade)
    employeeRepository.deleteById(employeeId);
    
    // Delete the associated SalatUser if it exists
    if (employee.getSalatUser() != null) {
      salatUserRepository.delete(employee.getSalatUser());
    }
  }

  @Authorized(requiresManager = true)
  public void createOrUpdate(Employee employee) {
    if(!employeeAuthorization.isAuthorized(employee, AccessLevel.WRITE)) {
      throw new RuntimeException("Illegal access to save " + employee.getId() + " by " + authorizedUser.getEmployeeId());
    }
    
    // Ensure SalatUser is persisted before saving Employee
    if (employee.getSalatUser() != null) {
      SalatUser salatUser = employee.getSalatUser();
      salatUserRepository.save(salatUser);
    }
    
    employeeRepository.save(employee);
  }
}
