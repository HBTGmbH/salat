package org.tb.employee.service;

import static org.tb.common.util.DateUtils.today;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;

@Service
@RequiredArgsConstructor
public class EmployeecontractService {

  private final EmployeecontractDAO employeecontractDAO;

  public Optional<Employeecontract> getCurrentContract(long employeeId) {
    return Optional.ofNullable(employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employeeId, today()));
  }

}
