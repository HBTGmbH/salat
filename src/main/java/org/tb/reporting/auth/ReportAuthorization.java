package org.tb.reporting.auth;

import static org.tb.common.util.DateUtils.today;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.AuthorizedUser;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.service.AuthService;
import org.tb.reporting.domain.ReportDefinition;

@Component
@AllArgsConstructor
public class ReportAuthorization {

  private final AuthorizedUser authorizedUser;
  private final AuthService authService;

  public boolean isAuthorized(ReportDefinition report, AccessLevel accessLevel) {
    if (authorizedUser.isManager()) {
      return true;
    }
    return authService.isAuthorized("REPORT_DEFINITION", today(), accessLevel, String.valueOf(report.getId()));
  }

  public boolean isAuthorizedForAnyReportDefinition(AccessLevel accessLevel) {
    if (authorizedUser.isManager()) {
      return true;
    }
    return authService.isAuthorizedAnyObject("REPORT_DEFINITION", today(), accessLevel);
  }

}
