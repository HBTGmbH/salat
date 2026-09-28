package de.hbt.salat.reporting.viewhelper;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.reporting.auth.ReportAuthorization;
import de.hbt.salat.reporting.domain.ReportDefinition;

@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class ReportAuthViewHelper {

  private final ReportAuthorization reportAuthorization;
  private final AuthorizedUser authorizedUser;

  public boolean isReportMenuAvailable() {
    return mayCreateNewReports() || reportAuthorization.isAuthorizedForAnyReportDefinition(AccessLevel.EXECUTE);
  }

  public boolean isAuth(ReportDefinition report, String accessLevel) {
    return reportAuthorization.isAuthorized(report, AccessLevel.valueOf(accessLevel));
  }

  public boolean mayCreateNewReports() {
    return authorizedUser.isPeopleLead();
  }

}
