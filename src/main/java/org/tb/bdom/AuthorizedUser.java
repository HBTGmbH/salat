package org.tb.bdom;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

import java.io.Serializable;
import lombok.Data;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Data
public class AuthorizedUser implements Serializable {

  private static final long serialVersionUID = 1L;

  private boolean authenticated;
  private Long employeeId;
  private String sign;
  private boolean restricted;
  private boolean admin;
  private boolean manager;

}
