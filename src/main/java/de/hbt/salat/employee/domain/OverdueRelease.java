package de.hbt.salat.employee.domain;

import de.hbt.salat.auth.domain.SalatUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A running contract whose release lags behind what is due (#215), as values: the automatic reminder
 * in {@code dailyreport} needs no more, and no entity of this module crosses the boundary (#1244).
 *
 * @param employeecontractId      the contract
 * @param salatUserId             the login of the person; {@code null} where the person has none
 * @param releasedUntil           how far the bookings are released
 * @param dueUntil                how far they should be ({@link Employeecontract#dueUntil()})
 * @param peopleLeadSalatUserIds  the logins of the contract's people leads, those without a login left out
 */
public record OverdueRelease(long employeecontractId, Long salatUserId, LocalDate releasedUntil, LocalDate dueUntil,
                             List<Long> peopleLeadSalatUserIds) {

  public static OverdueRelease of(Employeecontract contract) {
    return new OverdueRelease(contract.getId(), loginId(contract.getEmployee()), contract.getReportReleaseDate(),
        contract.dueUntil(), contract.getSupervisors().stream().map(OverdueRelease::loginId).filter(Objects::nonNull).toList());
  }

  private static Long loginId(Employee employee) {
    SalatUser login = employee.getSalatUser();
    return login != null ? login.getId() : null;
  }
}
