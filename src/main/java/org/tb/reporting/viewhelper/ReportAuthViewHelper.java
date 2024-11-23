package org.tb.reporting.viewhelper;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.tb.auth.AuthViewHelper;
import org.tb.auth.AuthorizedUser;
import org.tb.auth.domain.AccessLevel;
import org.tb.reporting.auth.ReportAuthorization;
import org.tb.reporting.domain.ReportDefinition;

@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class ReportAuthViewHelper implements AuthViewHelper {

  private final ReportAuthorization reportAuthorization;
  private final AuthorizedUser authorizedUser;

  public boolean isReportMenuAvailable() {
    return mayCreateNewReports() || reportAuthorization.isAuthorizedForAnyReportDefinition(AccessLevel.EXECUTE);
  }

  public boolean isAuth(ReportDefinition report, String accessLevel) {
    return reportAuthorization.isAuthorized(report, AccessLevel.valueOf(accessLevel));
  }

  public boolean mayCreateNewReports() {
    return authorizedUser.isManager();
  }

  @Override
  public String getName() {
    return "reportAuthViewHelper";
  }
}
