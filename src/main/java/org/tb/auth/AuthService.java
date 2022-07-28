package org.tb.auth;

import static org.tb.common.GlobalConstants.SUBORDER_INVOICE_YES;
import static org.tb.common.util.SecureHashUtils.legacyPasswordMatches;
import static org.tb.common.util.SecureHashUtils.passwordMatches;

import java.util.Optional;
import java.util.stream.StreamSupport;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final EmployeeRepository employeeRepository;
  private final EmployeeToEmployeeAuthorizationRuleRepository employeeToEmployeeAuthorizationRuleRepository;

  public Optional<Employee> authenticate(String loginname, String password) {
    var employeeResult = employeeRepository.findByLoginname(loginname);
    if(employeeResult.isPresent()) {
      var employee = employeeResult.get();
      var passwordMatches = passwordMatches(password, employee.getPassword());
      if (!passwordMatches) {
        // Fallback to legacy password matching - some users may not have been migrated yet!
        var legacyPasswordMatches = legacyPasswordMatches(password, employee.getPassword());
        if (legacyPasswordMatches) {
          // employee still has old password form
          // store password again with new hashing algorithm
          employee.changePassword(password);
          employeeRepository.save(employee);
          passwordMatches = true;
        }
      }
      if(passwordMatches) {
        return Optional.of(employee);
      }
    }
    return Optional.empty();
  }

  public boolean isAuthorized(Employee employee, AuthorizedUser user, AccessLevel accessLevel) {
    if(user.isManager()) return true;
    if(employee.isNew()) return false; // only managers can access newly created objects (without any id yet)
    if(employee.getId() == user.getEmployeeId()) return true;
    return StreamSupport.stream(employeeToEmployeeAuthorizationRuleRepository.findAll().spliterator(), false)
            .filter(rule -> rule.getGrantor() == employee)
            .filter(rule -> rule.getRecipient().getId() == user.getEmployeeId())
            .filter(rule -> rule.getAccessLevel().satisfies(accessLevel))
            .filter(rule -> rule.isValid(DateUtils.today()))
            .map(rule -> true)
            .findAny().orElse(false);
  }

  public boolean isAuthorized(Timereport timereport, AuthorizedUser user, AccessLevel accessLevel) {
    if(user.isManager()) return true;
    if(timereport.getEmployeecontract().getEmployee().getId() == user.getEmployeeId()) return true;

    if(accessLevel == AccessLevel.READ) {
      // every project manager may see the time reports of her project
      if(user.getEmployeeId().equals(timereport.getSuborder().getCustomerorder().getResponsible_hbt().getId())) {
        return true;
      }
      if(user.getEmployeeId().equals(timereport.getSuborder().getCustomerorder().getRespEmpHbtContract().getId())) {
        return true;
      }

      // backoffice users may see time reports that must be invoiced
      if(user.isBackoffice() && timereport.getSuborder().getInvoice() == SUBORDER_INVOICE_YES) {
        return true;
      }
    }

    return StreamSupport.stream(employeeToEmployeeAuthorizationRuleRepository.findAll().spliterator(), false)
            .filter(rule -> rule.getGrantor() == timereport.getEmployeecontract().getEmployee())
            .filter(rule -> rule.getRecipient().getId() == user.getEmployeeId())
            .filter(rule -> rule.getAccessLevel().satisfies(accessLevel))
            .filter(rule -> rule.isValid(timereport.getReferenceday().getRefdate()))
            .filter(rule -> rule.getCustomerOrder() == null || rule.getCustomerOrder() == timereport.getSuborder().getCustomerorder())
            .map(rule -> true)
            .findAny().orElse(false);
  }

}
