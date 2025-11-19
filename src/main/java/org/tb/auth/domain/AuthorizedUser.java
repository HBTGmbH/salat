package org.tb.auth.domain;

import static org.springframework.web.context.WebApplicationContext.SCOPE_SESSION;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_ADM;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BO;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_PV;

import java.io.Serializable;
import lombok.Getter;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@Scope(value = SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Getter
public class AuthorizedUser implements Serializable {

  private static final long serialVersionUID = 1L;

  private boolean authenticated;
  private String loginSign;
  private String loginStatus;
  private boolean restricted;
  private boolean backoffice;
  private boolean admin;
  private boolean manager;

  private String impersonateLoginSign;

  public String getEffectiveLoginSign() {
    return impersonateLoginSign != null ? impersonateLoginSign : loginSign;
  }

  public void impersonate(SalatUser user) {
    this.impersonateLoginSign = user.getLoginname();
    setPermissions(user);
  }

  public void login(SalatUser user) {
    this.loginSign = user.getLoginname();
    this.impersonateLoginSign = null;
    this.authenticated = true;
    setPermissions(user);
  }

  public void initForJob() {
    authenticated = true;
    loginSign = "SYSTEM";
    loginStatus = "job";
    restricted = false;
    admin = false;
    manager = true;
    backoffice = true;
    impersonateLoginSign = null;
  }

  private void setPermissions(SalatUser user) {
    this.restricted = user.isRestricted();
    this.loginStatus = user.getStatus();
    boolean isAdmin = loginStatus.equals(EMPLOYEE_STATUS_ADM);
    this.admin = isAdmin;
    boolean isManager = loginStatus.equals(EMPLOYEE_STATUS_BL) || loginStatus.equals(EMPLOYEE_STATUS_PV);
    this.manager = isAdmin || isManager;
    boolean isBackoffice = loginStatus.equals(EMPLOYEE_STATUS_BO);
    this.backoffice = isBackoffice || isManager || isAdmin;
  }

}
