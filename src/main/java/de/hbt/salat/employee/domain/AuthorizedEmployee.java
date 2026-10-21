package de.hbt.salat.employee.domain;

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
  /** How the person is named in notifications (#1505): by first name only. */
  @Getter
  private String firstname;
  @Getter
  private Long employeeId;
  @Getter
  private String sign;
  /** The id of the person's login, read off the reference without loading the login (#1501). */
  @Getter
  private Long salatUserId;

  public void login(Employee employee) {
    this.name = employee.getName();
    this.firstname = employee.getFirstname();
    this.employeeId = employee.getId();
    this.sign = employee.getSign();
    this.salatUserId = employee.getSalatUser() != null ? employee.getSalatUser().getId() : null;
  }
}
