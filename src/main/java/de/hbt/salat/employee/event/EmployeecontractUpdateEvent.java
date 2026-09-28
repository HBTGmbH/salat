package de.hbt.salat.employee.event;

import de.hbt.salat.common.event.DomainObjectUpdateEvent;
import de.hbt.salat.employee.domain.Employeecontract;

public class EmployeecontractUpdateEvent extends DomainObjectUpdateEvent<Employeecontract> {

  public EmployeecontractUpdateEvent(Employeecontract source) {
    super(source);
  }

}
