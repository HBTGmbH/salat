package org.tb.employee.event;

import lombok.Getter;

/**
 * The sign of an employee has changed (#966).
 *
 * <p>Records elsewhere pin an employee down by sign rather than by a foreign key — the cost
 * assignments and the customer rates of the budget module do. They cannot notice a sign that
 * changes underneath them and stop resolving from then on, which shows up as work costing 0 EUR in
 * controlling rather than as an error (#922). The event lets them follow.
 *
 * <p>Both signs travel along because that is what a follower needs to find and rewrite its rows.
 * The employee id travels along as well, so a follower that already holds a proper reference can
 * bind to the person instead of to either sign — which is where this is heading (#968).
 */
@Getter
public class EmployeeSignChangedEvent {

  private final long employeeId;
  private final String previousSign;
  private final String newSign;

  public EmployeeSignChangedEvent(long employeeId, String previousSign, String newSign) {
    this.employeeId = employeeId;
    this.previousSign = previousSign;
    this.newSign = newSign;
  }

}
