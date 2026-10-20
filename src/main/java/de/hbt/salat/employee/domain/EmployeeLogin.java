package de.hbt.salat.employee.domain;

import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_ADM;
import static de.hbt.salat.common.GlobalConstants.EMPLOYEE_STATUS_RESTRICTED;

/**
 * The login of a person as values (#1501), for a module that addresses the person through the
 * login — a notification goes to the login, not to the employee.
 */
public record EmployeeLogin(long employeeId, long salatUserId, String status) {

  public boolean isRestricted() {
    return EMPLOYEE_STATUS_RESTRICTED.equalsIgnoreCase(status);
  }

  /** The technical administrator is a login, not an employee of the company. */
  public boolean isAdministrator() {
    return EMPLOYEE_STATUS_ADM.equalsIgnoreCase(status);
  }
}
