package org.tb.employee.domain;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

import lombok.Getter;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class AuthorizedEmployee {

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
