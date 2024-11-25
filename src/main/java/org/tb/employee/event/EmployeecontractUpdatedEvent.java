package org.tb.employee.event;

import org.tb.common.event.DomainObjectUpdatedEvent;
import org.tb.employee.domain.Employeecontract;

public class EmployeecontractUpdatedEvent extends DomainObjectUpdatedEvent<Employeecontract> {

  public EmployeecontractUpdatedEvent(Employeecontract source) {
    super(source);
  }

}
