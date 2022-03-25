package org.tb.auth;

import static org.tb.common.util.SecureHashUtils.legacyPasswordMatches;
import static org.tb.common.util.SecureHashUtils.passwordMatches;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tb.common.util.SecureHashUtils;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final EmployeeDAO employeeDAO;

  public Optional<Employee> authenticate(String loginname, String password) {
    var employee = employeeDAO.getLoginEmployee(loginname);
    if(employee != null) {
      var passwordMatches = passwordMatches(password, employee.getPassword());
      if (!passwordMatches) {
        // Fallback to legacy password matching - some users may not have been migrated yet!
        var legacyPasswordMatches = legacyPasswordMatches(password, employee.getPassword());
        if (legacyPasswordMatches) {
          // employee still has old password form
          // store password again with new hashing algorithm
          employee.changePassword(password);
          employeeDAO.save(employee);
          passwordMatches = true;
        }
      }
      if(passwordMatches) {
        return Optional.of(employee);
      }
    }
    return Optional.empty();
  }

}
