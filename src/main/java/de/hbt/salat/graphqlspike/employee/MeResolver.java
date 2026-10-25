package de.hbt.salat.graphqlspike.employee;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.EmployeecontractPeriod;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.graphqlspike.config.GraphqlSpikeConfiguration;

/**
 * {@code me} (concept 4): the logged-in person from the request-scoped beans, the current contract through the
 * one entity-free method {@link EmployeecontractService} offers for it — there is no record with the contract's
 * details yet, only id and period.
 */
@Controller
@Profile(GraphqlSpikeConfiguration.PROFILE)
@Authorized
@RequiredArgsConstructor
class MeResolver {

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
        authorizedUser.getLoginStatus(), roles(), currentContractId);
  }

  private List<String> roles() {
    var roles = new ArrayList<String>();
    if (authorizedUser.isAdmin()) roles.add("ADMIN");
    if (authorizedUser.isManager()) roles.add("MANAGER");
    if (authorizedUser.isPeopleLead()) roles.add("PEOPLE_LEAD");
    if (authorizedUser.isBackoffice()) roles.add("BACKOFFICE");
    if (authorizedUser.isRestricted()) roles.add("RESTRICTED");
    return roles;
  }

  public record Me(long employeeId, String sign, String name, String status, List<String> roles, Long currentContractId) {
  }
}
