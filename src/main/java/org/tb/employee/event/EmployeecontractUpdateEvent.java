package org.tb.employee.event;

import org.tb.common.event.DomainObjectUpdateEvent;
import org.tb.employee.domain.Employeecontract;

public class EmployeecontractUpdateEvent extends DomainObjectUpdateEvent<Employeecontract> {

  public EmployeecontractUpdateEvent(Employeecontract source) {
    super(source);
  }

}
