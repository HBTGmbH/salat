package org.tb.bdom;

import lombok.Data;

@Data
public class AuthorizedUser {

  private final long employeeId;
  private final String sign;
  private final boolean admin;
  private final boolean manager;

}
