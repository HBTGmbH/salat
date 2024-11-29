package org.tb.auth.domain;

import static org.springframework.web.context.WebApplicationContext.SCOPE_SESSION;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_ADM;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BO;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_PV;

import java.io.Serializable;
import lombok.Data;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@Scope(value = SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Data
public class AuthorizedUser implements Serializable {

  private static final long serialVersionUID = 1L;

  private boolean authenticated;
  private String loginSign;
  private Long employeeId;
  private String sign;
  private boolean restricted;
  private boolean backoffice;
  private boolean admin;
  private boolean manager;

  public void login(String loginSign) {
    this.setLoginSign(loginSign);
    this.setSign(loginSign);
    this.setAuthenticated(true);
  }

  public void init(long employeeId, boolean restricted, String employeeStatus) {
    this.setEmployeeId(employeeId);
    this.setRestricted(restricted);
    boolean isAdmin = employeeStatus.equals(EMPLOYEE_STATUS_ADM);
    this.setAdmin(isAdmin);
    boolean isManager = employeeStatus.equals(EMPLOYEE_STATUS_BL) || employeeStatus.equals(EMPLOYEE_STATUS_PV);
    this.setManager(isAdmin || isManager);
    boolean isBackoffice = employeeStatus.equals(EMPLOYEE_STATUS_BO);
    this.setBackoffice(isBackoffice || isManager || isAdmin);
  }

}
