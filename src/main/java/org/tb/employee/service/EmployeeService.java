package org.tb.employee.service;

import static org.tb.auth.domain.AccessLevel.LOGIN;
import static org.tb.common.exception.ErrorCode.EM_DELETE_GOT_VETO;
import static org.tb.common.exception.ServiceFeedbackMessage.error;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.domain.AccessLevel;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.exception.VetoedException;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.domain.Employee;
import org.tb.employee.event.EmployeeDeleteEvent;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeeRepository;

@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeService {

  private final ApplicationEventPublisher eventPublisher;
  private final EmployeeRepository employeeRepository;
  private final EmployeeDAO employeeDAO;
  private final AuthorizedUser authorizedUser;
  private final EmployeeAuthorization employeeAuthorization;

  public Employee getLoginEmployee() {
    return employeeDAO.getLoginEmployee(authorizedUser.getLoginSign());
  }

  public List<Employee> getLoginEmployees() {
    return StreamSupport
        .stream(employeeRepository.findAll().spliterator(), false)
        .filter(e -> e.getSign().equals(authorizedUser.getLoginSign()) || employeeAuthorization.isAuthorized(e, LOGIN))
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
    employeeRepository.deleteById(employeeId);
  }

  public void createOrUpdate(Employee employee) {
    if(!employeeAuthorization.isAuthorized(employee, AccessLevel.WRITE)) {
      throw new RuntimeException("Illegal access to save " + employee.getId() + " by " + authorizedUser.getEmployeeId());
    }
    employeeRepository.save(employee);
  }
}
