package de.hbt.salat.employee.graphql;

import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_ADM;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_BO;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_MA;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_PV;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_RESTRICTED;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.EmployeecontractPeriod;
import de.hbt.salat.employee.service.EmployeecontractService;

/**
 * {@code me} (concept 4): the logged-in person from the request-scoped beans, the current contract through the
 * entity-free method {@link EmployeecontractService} offers for it — id and period, no details yet. Other modules
 * extend the type ({@code order}: what the person may book on).
 */
@Controller
@Authorized
@RequiredArgsConstructor
public class MeResolver {

  private final AuthorizedUser authorizedUser;
  private final AuthorizedEmployee authorizedEmployee;
  private final EmployeecontractService employeecontractService;

  @QueryMapping
  @Authorized
  Me me() {
    LocalDate today = DateUtils.today();
    Long currentContractId = employeecontractService
        .getEmployeecontractPeriodsBetween(authorizedEmployee.getEmployeeId(), today, today).stream()
        .findFirst()
        .map(EmployeecontractPeriod::employeecontractId)
        .orElse(null);
    return new Me(authorizedEmployee.getEmployeeId(), authorizedEmployee.getSign(), authorizedEmployee.getName(),
        LoginStatus.of(authorizedUser.getLoginStatus()), roles(), currentContractId);
  }

  private List<Role> roles() {
    var roles = new ArrayList<Role>();
    if (authorizedUser.isAdmin()) roles.add(Role.ADMIN);
    if (authorizedUser.isManager()) roles.add(Role.MANAGER);
    if (authorizedUser.isPeopleLead()) roles.add(Role.PEOPLE_LEAD);
    if (authorizedUser.isBackoffice()) roles.add(Role.BACKOFFICE);
    if (authorizedUser.isRestricted()) roles.add(Role.RESTRICTED);
    return roles;
  }

  public record Me(long employeeId, String sign, String name, LoginStatus status, List<Role> roles,
      Long currentContractId) {
  }

  /** The level of a login as the schema names it; the codes in the database are abbreviations. */
  public enum LoginStatus {
    ADMIN(EMPLOYEE_STATUS_ADM), MANAGEMENT(EMPLOYEE_STATUS_BL), PEOPLE_LEAD(EMPLOYEE_STATUS_PV),
    BACKOFFICE(EMPLOYEE_STATUS_BO), EMPLOYEE(EMPLOYEE_STATUS_MA), RESTRICTED(EMPLOYEE_STATUS_RESTRICTED);

    private final String code;

    LoginStatus(String code) {
      this.code = code;
    }

    static LoginStatus of(String code) {
      return Arrays.stream(values()).filter(status -> status.code.equalsIgnoreCase(code)).findFirst()
          .orElseThrow(() -> new IllegalStateException("Unknown login status " + code));
    }
  }

  public enum Role {
    ADMIN, MANAGER, PEOPLE_LEAD, BACKOFFICE, RESTRICTED
  }
}
