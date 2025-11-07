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
  private String loginStatus;
  private boolean restricted;
  private boolean backoffice;
  private boolean admin;
  private boolean manager;

  // data of the currently selected employee
  private String name;
  private Long employeeId;
  private String sign;

  public void login(SalatUser user) {
    this.setLoginSign(user.getLoginname());
    this.setSign(user.getLoginname());
    this.setRestricted(user.isRestricted());
    this.setLoginStatus(user.getStatus());
    this.setEmployeeId(null);
    this.setName(null);

    boolean isAdmin = loginStatus.equals(EMPLOYEE_STATUS_ADM);
    this.setAdmin(isAdmin);
    boolean isManager = loginStatus.equals(EMPLOYEE_STATUS_BL) || loginStatus.equals(EMPLOYEE_STATUS_PV);
    this.setManager(isAdmin || isManager);
    boolean isBackoffice = loginStatus.equals(EMPLOYEE_STATUS_BO);
    this.setBackoffice(isBackoffice || isManager || isAdmin);
    this.setAuthenticated(true);
  }

  public void init(long employeeId, String name) {
    this.setEmployeeId(employeeId);
    this.setName(name);
  }

}
