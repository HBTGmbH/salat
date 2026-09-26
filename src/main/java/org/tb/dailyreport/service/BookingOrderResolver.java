package org.tb.dailyreport.service;

import static org.tb.common.exception.ErrorCode.TR_BOOKING_AMBIGUOUS_EMPLOYEE_ORDER;
import static org.tb.common.exception.ErrorCode.TR_BOOKING_EMPLOYEE_UNKNOWN;
import static org.tb.common.exception.ErrorCode.TR_BOOKING_NO_CONTRACT;
import static org.tb.common.exception.ErrorCode.TR_BOOKING_NO_EMPLOYEE_ORDER;
import static org.tb.common.exception.ErrorCode.TR_BOOKING_OF_OTHER_EMPLOYEE;
import static org.tb.common.exception.ErrorCode.TR_BOOKING_ORDER_CONTRADICTS_SIGN;
import static org.tb.common.exception.ErrorCode.TR_BOOKING_ORDER_NOT_NAMED;
import static org.tb.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_NOT_FOUND;

import java.time.LocalDate;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.rest.DailyReportData;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.service.EmployeeorderService;

/**
 * Finds the employee order a booking of the import or the REST API belongs to (#1142): by the id of
 * the order, or by two readable values — the complete sign of the suborder and the sign of the
 * employee. The employee's contract valid on the day of the booking, and among its orders the one
 * valid on that day whose suborder carries exactly that sign, is the order. Contract and order
 * follow from the day, so a file spanning a change of contract assigns each day to its own contract.
 *
 * <p>None and several matches are both rejected: a complete sign is unique neither in the database
 * nor in the forms, and choosing one of several orders silently would book on a guess.
 *
 * <p>This decides nothing about authorization. The booking is created afterwards through
 * {@link TimereportService} exactly as one named by its id, so whoever names an order by its signs
 * may book for the same employees as before and no one else.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized
public class BookingOrderResolver {

  private final EmployeeService employeeService;
  private final EmployeecontractService employeecontractService;
  private final EmployeeorderService employeeorderService;
  private final AuthorizedEmployee authorizedEmployee;

  /**
   * The rule of the REST API. A given id takes precedence and the signs are not evaluated: a client
   * that has always sent the signs of an older read along with the id must not start failing now. A
   * missing employee sign stands for the authenticated employee, as the lists of the API do.
   */
  public Employeeorder resolve(DailyReportData booking, LocalDate day) {
    if (booking.getEmployeeorderId() != null) {
      return byId(booking.getEmployeeorderId());
    }
    var employeeSign = isBlank(booking.getEmployeeSign()) ? authorizedEmployee.getSign() : booking.getEmployeeSign();
    return bySigns(employeeBySign(employeeSign), booking.getSuborderSign(), day);
  }

  /**
   * The rule of the CSV import of the UI, where the page has a contract selected and the file belongs
   * to its {@code employee}. An employee sign in the file that names someone else is rejected, so is an
   * id whose order belongs to someone else.
   *
   * <p>The id takes precedence here too, but a suborder sign in the same line has to agree with it. A
   * file is written by hand: whoever copies a line and changes its sign has changed the order, and the
   * id left behind must not quietly book it on the old one. An older file whose suborder has been renamed
   * since fails in the same way, which is the price of never booking on a guess.
   */
  public Employeeorder resolveFor(Employee employee, DailyReportData booking, LocalDate day) {
    if (!isBlank(booking.getEmployeeSign())) {
      var named = employeeBySign(booking.getEmployeeSign());
      if (!Objects.equals(named.getId(), employee.getId())) {
        throw new InvalidDataException(TR_BOOKING_OF_OTHER_EMPLOYEE, named.getSign(), employee.getSign());
      }
    }
    if (booking.getEmployeeorderId() == null) {
      return bySigns(employee, booking.getSuborderSign(), day);
    }
    var employeeorder = byId(booking.getEmployeeorderId());
    var owner = employeeorder.getEmployeecontract().getEmployee();
    if (!Objects.equals(owner.getId(), employee.getId())) {
      throw new InvalidDataException(TR_BOOKING_OF_OTHER_EMPLOYEE, owner.getSign(), employee.getSign());
    }
    var completeOrderSign = employeeorder.getSuborder().getCompleteOrderSign();
    if (!isBlank(booking.getSuborderSign()) && !booking.getSuborderSign().strip().equals(completeOrderSign)) {
      throw new InvalidDataException(TR_BOOKING_ORDER_CONTRADICTS_SIGN,
          employeeorder.getId(), completeOrderSign, booking.getSuborderSign().strip());
    }
    return employeeorder;
  }

  private Employeeorder byId(long employeeorderId) {
    var employeeorder = employeeorderService.getEmployeeorderById(employeeorderId);
    if (employeeorder == null) {
      throw new InvalidDataException(TR_EMPLOYEE_ORDER_NOT_FOUND);
    }
    return employeeorder;
  }

  private Employee employeeBySign(String sign) {
    var employee = isBlank(sign) ? null : employeeService.getEmployeeBySign(sign.strip());
    if (employee == null) {
      throw new InvalidDataException(TR_BOOKING_EMPLOYEE_UNKNOWN, sign);
    }
    return employee;
  }

  private Employeeorder bySigns(Employee employee, String suborderSign, LocalDate day) {
    var date = DateUtils.format(day);
    if (isBlank(suborderSign)) {
      throw new InvalidDataException(TR_BOOKING_ORDER_NOT_NAMED, date);
    }
    var contract = employeecontractService.getEmployeeContractValidAt(employee.getId(), day);
    if (contract == null) {
      throw new InvalidDataException(TR_BOOKING_NO_CONTRACT, employee.getSign(), date);
    }
    var sign = suborderSign.strip();
    var candidates = employeeorderService.getEmployeeordersByCompleteOrderSignValidAt(contract.getId(), sign, day);
    if (candidates.isEmpty()) {
      throw new InvalidDataException(TR_BOOKING_NO_EMPLOYEE_ORDER, sign, employee.getSign(), date);
    }
    if (candidates.size() > 1) {
      throw new InvalidDataException(TR_BOOKING_AMBIGUOUS_EMPLOYEE_ORDER, sign, employee.getSign(), date);
    }
    return candidates.getFirst();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
