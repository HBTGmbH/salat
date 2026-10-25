package de.hbt.salat.dailyreport.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.service.EmployeecontractService;

/**
 * The own bookings of a period (#1532), on one order or on all, a page at a time (#1539).
 *
 * <p>The bookings are read as the booking pages read them, per contract through the cached query, and filtered, ordered
 * and cut here. A year of own bookings is a few thousand rows at most, and the query reads them anyway; what the page
 * spares is the answer, not the database.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized
public class OwnTimereportService {

  /**
   * Day, order, suborder, and the id last: across the contracts of the period, and so that two bookings alike in
   * everything else keep their places and a page neither repeats nor skips one.
   */
  private static final Comparator<TimereportDTO> ORDER = Comparator.comparing(TimereportDTO::getReferenceday)
      .thenComparing(TimereportDTO::getCustomerorderSign)
      .thenComparing(TimereportDTO::getCompleteOrderSign)
      .thenComparingLong(TimereportDTO::getId);

  private final EmployeecontractService employeecontractService;
  private final TimereportService timereportService;
  private final AuthorizedEmployee authorizedEmployee;

  /**
   * @param customerorderSign the sign of the order whose tree the bookings lie in, matched exactly; {@code null} for all
   * @param offset            how many bookings to skip
   * @param maxResults        how many bookings at most
   */
  public List<TimereportDTO> getOwnTimereports(LocalDate from, LocalDate until, String customerorderSign, int offset,
      int maxResults) {
    return employeecontractService.getEmployeecontractPeriodsBetween(authorizedEmployee.getEmployeeId(), from, until)
        .stream()
        .flatMap(period -> timereportService
            .getTimereportsByDatesAndEmployeeContractId(period.employeecontractId(), period.from(), period.until())
            .stream())
        .filter(timereport -> customerorderSign == null || customerorderSign.equals(timereport.getCustomerorderSign()))
        .sorted(ORDER)
        .skip(offset)
        .limit(maxResults)
        .toList();
  }
}
