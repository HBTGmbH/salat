package de.hbt.salat.dailyreport.auth;

import static de.hbt.salat.common.util.DateUtils.today;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.employee.domain.Employeecontract;

@Component
@RequiredArgsConstructor
public class ReleaseAuthorization {

  private static final String AUTH_CATEGORY_RELEASE = "RELEASE_TIMEREPORTS";
  private static final String AUTH_CATEGORY_ACCEPT = "ACCEPT_TIMEREPORTS";

  private final AuthorizedUser authorizedUser;
  private final AuthService authService;

  public boolean isReleaseAuthorized(Employeecontract employeecontract, AccessLevel accessLevel) {
    if(authorizedUser.getEffectiveLoginSign().equals(employeecontract.getEmployee().getSalatUser().getLoginname())) return true;
    if (authorizedUser.isManager()) return true;
    if (authorizedUser.isPeopleLead() && isSupervisedByCurrentUser(employeecontract)) return true;
    if (authorizedUser.isAdmin()) return true;
    // the person by id, not by sign: a sign changes and may be given to somebody else (#1204)
    return authService.isAuthorized(AUTH_CATEGORY_RELEASE, today(), accessLevel, employeeIdOf(employeecontract));
  }

  public boolean isAcceptAuthorized(Employeecontract employeecontract, AccessLevel accessLevel) {
    if(authorizedUser.getEffectiveLoginSign().equals(employeecontract.getEmployee().getSalatUser().getLoginname())) return false; // cannot accept own hours
    if (authorizedUser.isManager()) return true;
    if (authorizedUser.isPeopleLead() && isSupervisedByCurrentUser(employeecontract)) return true;
    if (authorizedUser.isAdmin()) return true;
    return authService.isAuthorized(AUTH_CATEGORY_ACCEPT, today(), accessLevel, employeeIdOf(employeecontract));
  }

  private static String employeeIdOf(Employeecontract employeecontract) {
    return String.valueOf(employeecontract.getEmployee().getId());
  }

  private boolean isSupervisedByCurrentUser(Employeecontract ec) {
    return ec.getSupervisors().stream()
        .anyMatch(s -> s.getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign()));
  }

}
