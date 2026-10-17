package de.hbt.salat.order.auth;

import static de.hbt.salat.auth.domain.AccessLevel.READ;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser_;
import de.hbt.salat.employee.domain.Employee_;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.domain.Employeecontract_;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Employeeorder_;

@Component
@RequiredArgsConstructor
public class EmployeeorderAuthorization {

  private final AuthorizedUser authorizedUser;

  public boolean isAuthorized(Employeeorder employeeorder, AccessLevel accessLevel) {
    if (authorizedUser.isManager()) return true;
    if (accessLevel == READ && authorizedUser.isPeopleLead() && isSupervisedByCurrentUser(employeeorder.getEmployeecontract())) return true;
    return employeeorder.getEmployeecontract().getEmployee().getSalatUser().getLoginname()
        .equals(authorizedUser.getEffectiveLoginSign());
  }

  /**
   * {@link #isAuthorized} with {@code READ}, as a condition of the query (#1380): the employee order list asks the
   * database for the rows the user may read and counts them there, instead of loading every row and dropping the
   * others in Java (docs/performance-tips.md §3). A manager reads all of them, a people lead those of the contracts
   * they supervise, everybody their own. The two must say the same; {@code EmployeeorderReadableTest} holds them
   * together.
   */
  public Specification<Employeeorder> readable() {
    if (authorizedUser.isManager()) {
      return (root, query, builder) -> builder.conjunction();
    }
    var loginSign = authorizedUser.getEffectiveLoginSign();
    var peopleLead = authorizedUser.isPeopleLead();
    return (root, query, builder) -> {
      var contract = root.join(Employeeorder_.employeecontract);
      var own = builder.equal(
          contract.join(Employeecontract_.employee).join(Employee_.salatUser).get(SalatUser_.loginname), loginSign);
      if (!peopleLead) {
        return own;
      }
      var supervised = query.subquery(Integer.class);
      var supervisor = supervised.correlate(contract).join(Employeecontract_.supervisors);
      supervised.select(builder.literal(1))
          .where(builder.equal(supervisor.join(Employee_.salatUser).get(SalatUser_.loginname), loginSign));
      return builder.or(own, builder.exists(supervised));
    };
  }

  private boolean isSupervisedByCurrentUser(Employeecontract ec) {
    return ec.getSupervisors().stream()
        .anyMatch(s -> s.getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign()));
  }

}
