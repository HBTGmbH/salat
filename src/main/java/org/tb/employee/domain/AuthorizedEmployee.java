package org.tb.employee.domain;

import static org.springframework.web.context.WebApplicationContext.SCOPE_SESSION;

import java.io.Serializable;
import lombok.Getter;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@Scope(value = SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class AuthorizedEmployee implements Serializable {

  private static final long serialVersionUID = 1L;

  @Getter
  private String name;
  @Getter
  private Long employeeId;
  @Getter
  private String sign;
  @Getter
  private String emailAddress;

  public void login(Employee employee) {
    this.name = employee.getName();
    this.employeeId = employee.getId();
    this.sign = employee.getSign();
    this.emailAddress = employee.getEmailAddress();
  }
}
