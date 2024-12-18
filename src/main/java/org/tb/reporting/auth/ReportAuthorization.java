package org.tb.reporting.auth;

import static java.lang.String.valueOf;
import static java.util.Comparator.comparing;
import static org.tb.common.util.DateUtils.today;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.common.LocalDateRange;
import org.tb.reporting.domain.ReportDefinition;

@Component
@AllArgsConstructor
public class ReportAuthorization {

  private static final String AUTH_CATEGORY_REPORT_DEFINITION = "REPORT_DEFINITION";

  private final AuthorizedUser authorizedUser;
  private final AuthService authService;

  public boolean isAuthorized(ReportDefinition report, AccessLevel accessLevel) {
    if (authorizedUser.isManager()) {
      return true;
    }
    return authService.isAuthorized(AUTH_CATEGORY_REPORT_DEFINITION, today(), accessLevel, valueOf(report.getId()));
  }

  public boolean isAuthorizedForAnyReportDefinition(AccessLevel accessLevel) {
    if (authorizedUser.isManager()) {
      return true;
    }
    return authService.isAuthorizedAnyObject(AUTH_CATEGORY_REPORT_DEFINITION, today(), accessLevel);
  }

  public List<ReportAuthorizationInfo> getAuthorizations(ReportDefinition report) {
    var rules = authService.getAuthRules(AUTH_CATEGORY_REPORT_DEFINITION, valueOf(report.getId()));
    return rules.stream()
        .map(r -> new ReportAuthorizationInfo(
            r.getGranteeId(),
            r.getAccessLevel(),
            r.getValidity()
        ))
        .sorted(
            comparing(ReportAuthorizationInfo::getUserSign)
                .thenComparing(ReportAuthorizationInfo::getAccessLevel)
                .thenComparing(ReportAuthorizationInfo::getValidity)
        )
        .toList();
  }

  @Data
  public static class ReportAuthorizationInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String userSign;
    private final AccessLevel accessLevel;
    private final LocalDateRange validity;

  }

}
