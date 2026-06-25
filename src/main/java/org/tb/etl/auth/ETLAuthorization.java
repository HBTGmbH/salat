package org.tb.etl.auth;

import static org.tb.common.util.DateUtils.today;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.employee.domain.Employeecontract;
import org.tb.etl.domain.ETLDefinition;

@Component
@RequiredArgsConstructor
public class ETLAuthorization {

  private static final String AUTH_CATEGORY = "ETL";

  private final AuthorizedUser authorizedUser;
  private final AuthService authService;

  public boolean isAuthorized(ETLDefinition etlDefinition, AccessLevel accessLevel) {
    if (authorizedUser.isManager()) return true;
    if (authorizedUser.isAdmin()) return true;
    return authService.isAuthorized(AUTH_CATEGORY, today(), accessLevel, etlDefinition.getName());
  }

}
