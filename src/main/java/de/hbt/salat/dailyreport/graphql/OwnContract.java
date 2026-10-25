package de.hbt.salat.dailyreport.graphql;

import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_CONTRACT_NOT_FOUND;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.EmployeecontractPeriod;
import de.hbt.salat.employee.service.EmployeecontractService;

/**
 * The contract of the logged-in person on a day, as its id — what the resolvers of this module book and keep the
 * working day on. Asked through the one entity-free method of {@link EmployeecontractService} (ADR-0021).
 */
@Component
@RequiredArgsConstructor
class OwnContract {

  private final EmployeecontractService employeecontractService;
  private final AuthorizedEmployee authorizedEmployee;

  /** @throws InvalidDataException {@code TR-0002} where the person has no contract on the day */
  long on(LocalDate date) {
    return employeecontractService.getEmployeecontractPeriodsBetween(authorizedEmployee.getEmployeeId(), date, date)
        .stream()
        .findFirst()
        .map(EmployeecontractPeriod::employeecontractId)
        .orElseThrow(() -> new InvalidDataException(TR_EMPLOYEE_CONTRACT_NOT_FOUND));
  }
}
