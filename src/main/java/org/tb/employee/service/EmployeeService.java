package org.tb.employee.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tb.auth.AuthorizedUser;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;

@Service
@RequiredArgsConstructor
public class EmployeeService {

  private final EmployeeDAO employeeDAO;
  private final AuthorizedUser authorizedUser;

  public Employee getLoginEmployee() {
    return employeeDAO.getLoginEmployee(authorizedUser.getLoginSign());
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
