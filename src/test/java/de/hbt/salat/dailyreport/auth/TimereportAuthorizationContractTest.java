package de.hbt.salat.dailyreport.auth;

import static de.hbt.salat.auth.domain.AccessLevel.READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.dailyreport.domain.Referenceday;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * „Meine Konten“ reads the bookings of a contract as values and checks the contract once instead of each booking
 * (#1514). {@link TimereportAuthorization#mayReadEveryBookingOf} must say yes only where {@code isAuthorized} with
 * {@code READ} lets every booking of the contract through.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportAuthorizationContractTest {

  @Mock
  private AuthorizedUser authorizedUser;

  @Mock
  private AuthService authService;

  @Test
  void a_manager_reads_every_booking() {
    as("mgr", true, false);

    assertMayReadEveryBooking(contract("aa", "pl"));
  }

  @Test
  void the_person_reads_every_booking_of_their_own_contract() {
    as("aa", false, false);

    assertMayReadEveryBooking(contract("aa", "pl"));
  }

  @Test
  void a_people_lead_reads_every_booking_of_a_contract_they_supervise() {
    as("pl", false, true);

    assertMayReadEveryBooking(contract("aa", "pl"));
  }

  @Test
  void a_people_lead_does_not_read_a_contract_somebody_else_supervises() {
    as("pl", false, true);

    assertThat(authorization().mayReadEveryBookingOf(contract("aa", "other"))).isFalse();
  }

  /** Being named supervisor is not enough — without the role it gives no right to read. */
  @Test
  void a_supervisor_without_the_role_does_not_read_the_contract() {
    as("pl", false, false);

    assertThat(authorization().mayReadEveryBookingOf(contract("aa", "pl"))).isFalse();
  }

  /** Responsible for the order of one booking, a person reads that booking - not the contract with all others. */
  @Test
  void responsibility_for_an_order_does_not_open_the_whole_contract() {
    as("resp", false, false);
    var contract = contract("aa", "pl");
    var booking = booking(contract);
    booking.getSuborder().getCustomerorder().setResponsibleHbt(List.of(employee("resp")));

    assertThat(authorization().isAuthorized(booking, READ)).isTrue();
    assertThat(authorization().mayReadEveryBookingOf(contract)).isFalse();
  }

  private void assertMayReadEveryBooking(Employeecontract contract) {
    assertThat(authorization().mayReadEveryBookingOf(contract)).isTrue();
    assertThat(authorization().isAuthorized(booking(contract), READ)).isTrue();
  }

  private TimereportAuthorization authorization() {
    return new TimereportAuthorization(authorizedUser, authService, mock(EmployeecontractService.class));
  }

  private void as(String loginSign, boolean manager, boolean peopleLead) {
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(loginSign);
    when(authorizedUser.isManager()).thenReturn(manager);
    when(authorizedUser.isPeopleLead()).thenReturn(peopleLead);
  }

  private static Timereport booking(Employeecontract contract) {
    var customerorder = new Customerorder();
    customerorder.setCustomer(new Customer());
    customerorder.setRespEmpHbtContract(employee("nobody"));
    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    var employeeorder = new Employeeorder();
    employeeorder.setEmployeecontract(contract);
    employeeorder.setSuborder(suborder);
    var timereport = new Timereport();
    timereport.setEmployeeorder(employeeorder);
    var day = new Referenceday();
    day.setRefdate(LocalDate.parse("2026-03-02"));
    timereport.setReferenceday(day);
    return timereport;
  }

  private static Employeecontract contract(String sign, String supervisorSign) {
    var contract = new Employeecontract();
    contract.setEmployee(employee(sign));
    contract.setSupervisors(List.of(employee(supervisorSign)));
    return contract;
  }

  private static Employee employee(String sign) {
    var login = new SalatUser();
    login.setLoginname(sign);
    var employee = new Employee();
    employee.setSign(sign);
    employee.setSalatUser(login);
    return employee;
  }
}
