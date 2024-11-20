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
}
