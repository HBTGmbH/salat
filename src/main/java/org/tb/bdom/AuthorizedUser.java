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

  private boolean authenticated = false;
  private long employeeId = -1;
  private String sign = null;
  private boolean restricted = false;
  private boolean admin = false;
  private boolean manager = false;

}
