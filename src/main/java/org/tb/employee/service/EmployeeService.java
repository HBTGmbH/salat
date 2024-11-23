package org.tb.employee.service;

import static org.tb.auth.domain.AccessLevel.LOGIN;

import java.util.List;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tb.auth.AuthorizedUser;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeeRepository;

@Service
@RequiredArgsConstructor
public class EmployeeService {

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

  public boolean deleteEmployeeById(long employeeId) {
    return employeeDAO.deleteEmployeeById(employeeId);
  }

  public void save(Employee employee) {
    employeeDAO.save(employee);
  }
}
