package org.tb.auth;

import static java.lang.Boolean.TRUE;
import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_ADM;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BO;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_PV;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_RESTRICTED;

import java.io.Serializable;
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
  private Long employeeId;
  private String sign;
  private String name;
  private boolean restricted;
  private boolean backoffice;
  private boolean admin;
  private boolean manager;

  public void init(Employee loginEmployee) {
    authenticated = true;
    employeeId = loginEmployee.getId();
    sign = loginEmployee.getSign();
    name = loginEmployee.getName();
    restricted = TRUE.equals(loginEmployee.getRestricted()) || loginEmployee.getStatus().equals(EMPLOYEE_STATUS_RESTRICTED);
    boolean isAdmin = loginEmployee.getStatus().equals(EMPLOYEE_STATUS_ADM);
    admin = isAdmin;
    boolean isManager = loginEmployee.getStatus().equals(EMPLOYEE_STATUS_BL) || loginEmployee.getStatus().equals(EMPLOYEE_STATUS_PV);
    manager = isAdmin || isManager;
    boolean isBackoffice = loginEmployee.getStatus().equals(EMPLOYEE_STATUS_BO);
    backoffice = isBackoffice || isManager || isAdmin;
  }

  public void invalidate() {
    authenticated = false;
    employeeId = null;
    sign = null;
    restricted = false;
    backoffice = false;
    admin = false;
    manager = false;
  }

}
