package org.tb.auth;

import static java.lang.Boolean.TRUE;
import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_ADM;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BO;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_PV;

import java.io.Serializable;
import java.util.Set;
import lombok.Data;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.tb.employee.domain.Employee;

@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
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
  private Set<UserRole> userRoles;

  public void login(String loginSign) {
    this.setLoginSign(loginSign);
  }

  public void init(Employee loginEmployee, Set<UserRole> userRoles) {
    this.setAuthenticated(true);
    this.setEmployeeId(loginEmployee.getId());
    this.setSign(loginEmployee.getSign());
    this.setRestricted(TRUE.equals(loginEmployee.getRestricted()));
    boolean isAdmin = loginEmployee.getStatus().equals(EMPLOYEE_STATUS_ADM);
    this.setAdmin(isAdmin);
    boolean isManager = loginEmployee.getStatus().equals(EMPLOYEE_STATUS_BL) || loginEmployee.getStatus().equals(EMPLOYEE_STATUS_PV);
    this.setManager(isAdmin || isManager);
    boolean isBackoffice = loginEmployee.getStatus().equals(EMPLOYEE_STATUS_BO);
    this.setBackoffice(isBackoffice || isManager || isAdmin);
    this.setUserRoles(userRoles);
  }

}
