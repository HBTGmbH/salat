package org.tb.auth;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.tb.reporting.domain.ReportDefinition;

@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class AuthViewHelper {

  private final AuthService authService;

  public boolean isReportMenuAvailable() {
    return authService.isAuthorizedForAnyReportDefinition(AccessLevel.EXECUTE);
  }

  public boolean isAuth(ReportDefinition report, String accessLevel) {
    return authService.isAuthorized(report, AccessLevel.valueOf(accessLevel));
  }

}
