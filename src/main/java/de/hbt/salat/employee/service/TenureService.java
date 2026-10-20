package de.hbt.salat.employee.service;

import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.EmployeeEntryDate;
import de.hbt.salat.employee.domain.EmploymentSpan;
import de.hbt.salat.employee.domain.Tenure;
import de.hbt.salat.employee.persistence.EmployeeEntryRepository;
import de.hbt.salat.employee.persistence.EmployeecontractRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How long people have been with the company (#1501), from their contracts and — for an entry before
 * Salat — from {@code employee_entry}. See {@link Tenure} for how the years are counted.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Authorized
public class TenureService {

  private final EmployeecontractRepository employeecontractRepository;
  private final EmployeeEntryRepository employeeEntryRepository;
  private final AuthorizedUser authorizedUser;
  private final AuthorizedEmployee authorizedEmployee;

  /** The tenure of every person with a contract valid on the day, by employee id — for the nightly job. */
  @Authorized(requiresManager = true)
  public Map<Long, Tenure> getTenuresOfEmployeesEmployedOn(LocalDate day) {
    var entryDates = employeeEntryRepository.findAllEntryDates().stream()
        .collect(toMap(EmployeeEntryDate::employeeId, EmployeeEntryDate::entryDate));
    return employeecontractRepository.findSpansOfEmployeesEmployedOn(day).stream()
        .collect(groupingBy(EmploymentSpan::employeeId)).entrySet().stream()
        .collect(toMap(Map.Entry::getKey, entry -> Tenure.of(entry.getValue(), entryDates.get(entry.getKey()))));
  }

  /**
   * The tenure of one person, for their profile page. The entry date is personal data: only the
   * person — or whoever acts as them — and management may read it.
   */
  public Tenure getTenure(long employeeId) {
    if (!authorizedUser.isManager() && !Objects.equals(authorizedEmployee.getEmployeeId(), employeeId)) {
      throw new AuthorizationException(AA_NOT_ATHORIZED);
    }
    return Tenure.of(employeecontractRepository.findSpansByEmployeeId(employeeId),
        employeeEntryRepository.findEntryDateByEmployeeId(employeeId).orElse(null));
  }
}
