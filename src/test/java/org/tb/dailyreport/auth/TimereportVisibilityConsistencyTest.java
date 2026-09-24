package org.tb.dailyreport.auth;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.tb.auth.domain.AccessLevel.READ;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.tb.auth.domain.AuthorizationRule;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizationRuleRepository;
import org.tb.auth.service.AuthService;
import org.tb.common.LocalDateRange;
import org.tb.common.SalatProperties;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * The booking list does not ask {@link TimereportAuthorization} per row — it turns the same rule into a condition of
 * its query, {@link TimereportVisibility} (#1092). Two expressions of one rule can drift apart without anything
 * failing, and this test is what makes that visible: for every constellation both must say the same.
 *
 * <p>Whoever changes one of the two runs this. A new rung on the ladder of {@code isAuthorized} that is missing from
 * the scope shows up here as a booking the list hides although the person may read it — or, worse the other way
 * round, as one it shows although they may not.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class TimereportVisibilityConsistencyTest {

  private static final String READER = "reader";
  private static final long READER_EMPLOYEE_ID = 1L;
  private static final String BOOKING_EMPLOYEE_SIGN = "xx";
  private static final long BOOKING_EMPLOYEE_ID = 2L;
  private static final String CUSTOMER_ORDER_SIGN = "1453";
  private static final long CUSTOMER_ORDER_ID = 10L;
  private static final String SUBORDER_SIGN = "1453/01";
  private static final long SUBORDER_ID = 100L;
  private static final LocalDate BOOKING_DATE = of(2011, 1, 2);
  private static final LocalDateRange PERIOD = new LocalDateRange(of(2011, 1, 1), of(2011, 1, 31));

  @Mock
  private AuthorizedUser authorizedUser;
  @Mock
  private AuthorizedEmployee authorizedEmployee;
  @Mock
  private AuthorizationRuleRepository authorizationRuleRepository;
  @Mock
  private SalatProperties salatProperties;
  @Mock
  private EmployeeService employeeService;
  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private CustomerorderService customerorderService;
  @Mock
  private SuborderService suborderService;
  @Mock(answer = RETURNS_DEEP_STUBS)
  private Timereport timereport;

  private TimereportAuthorization timereportAuthorization;
  private TimereportVisibilityService visibilityService;

  @BeforeEach
  void setUp() {
    var authServiceProps = new SalatProperties.AuthService();
    authServiceProps.setCacheExpiry(Duration.ofMillis(1000));
    when(salatProperties.getAuthService()).thenReturn(authServiceProps);
    when(authorizedUser.getLoginSign()).thenReturn(READER);
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(READER);
    when(authorizedEmployee.getEmployeeId()).thenReturn(READER_EMPLOYEE_ID);
    when(employeecontractService.getSupervisedEmployeeIds(anyLong())).thenReturn(Set.of());
    when(customerorderService.getIdsByResponsibleEmployeeId(anyLong())).thenReturn(List.of());
    when(authorizationRuleRepository.findAll()).thenReturn(List.of());

    var authService = new AuthService(authorizedUser, authorizationRuleRepository, null, salatProperties, null, null);
    authService.init();
    timereportAuthorization = new TimereportAuthorization(authorizedUser, authService);
    visibilityService = new TimereportVisibilityService(authorizedUser, authorizedEmployee, authService,
        employeeService, employeecontractService, customerorderService, suborderService);

    // a booking of somebody else, so nothing but the rules can cover it
    when(timereport.getEmployeecontract().getEmployee().getId()).thenReturn(BOOKING_EMPLOYEE_ID);
    when(timereport.getEmployeecontract().getEmployee().getSign()).thenReturn(BOOKING_EMPLOYEE_SIGN);
    when(timereport.getEmployeecontract().getEmployee().getSalatUser().getLoginname()).thenReturn("somebody-else");
    when(timereport.getSuborder().getId()).thenReturn(SUBORDER_ID);
    when(timereport.getSuborder().isInvoiceable()).thenReturn(false);
    when(timereport.getSuborder().getCompleteOrderSign()).thenReturn(SUBORDER_SIGN);
    when(timereport.getSuborder().getCustomerorder().getId()).thenReturn(CUSTOMER_ORDER_ID);
    when(timereport.getSuborder().getCustomerorder().getSign()).thenReturn(CUSTOMER_ORDER_SIGN);
    when(timereport.getSuborder().getCustomerorder().getResponsibleHbt()).thenReturn(List.of());
    when(timereport.getReferenceday().getRefdate()).thenReturn(BOOKING_DATE);

    var bookingEmployee = new Employee();
    setField(bookingEmployee, "id", BOOKING_EMPLOYEE_ID);
    when(employeeService.getEmployeeBySign(BOOKING_EMPLOYEE_SIGN)).thenReturn(bookingEmployee);

    var customerorder = new Customerorder();
    setField(customerorder, "id", CUSTOMER_ORDER_ID);
    when(customerorderService.getCustomerorderBySign(CUSTOMER_ORDER_SIGN)).thenReturn(customerorder);

    var suborder = new Suborder();
    setField(suborder, "id", SUBORDER_ID);
    when(suborderService.getSuborderByCompleteOrderSign(SUBORDER_SIGN)).thenReturn(suborder);
  }

  @Test
  void managerSeesEverything() {
    when(authorizedUser.isManager()).thenReturn(true);

    assertBothAgree(true);
  }

  @Test
  void withoutAnyRuleNobodySeesTheBookingOfSomebodyElse() {
    assertBothAgree(false);
  }

  @Test
  void ownBookingIsVisible() {
    when(timereport.getEmployeecontract().getEmployee().getId()).thenReturn(READER_EMPLOYEE_ID);
    when(timereport.getEmployeecontract().getEmployee().getSalatUser().getLoginname()).thenReturn(READER);

    assertBothAgree(true);
  }

  @Test
  void responsibleForTheOrderSeesWhatIsBookedOnIt() {
    var responsible = new Employee();
    setField(responsible, "id", READER_EMPLOYEE_ID);
    var salatUser = new org.tb.auth.domain.SalatUser();
    salatUser.setLoginname(READER);
    responsible.setSalatUser(salatUser);
    when(timereport.getSuborder().getCustomerorder().getResponsibleHbt()).thenReturn(List.of(responsible));
    when(customerorderService.getIdsByResponsibleEmployeeId(READER_EMPLOYEE_ID)).thenReturn(List.of(CUSTOMER_ORDER_ID));

    assertBothAgree(true);
  }

  @Test
  void backofficeSeesWhatHasToBeInvoiced() {
    when(authorizedUser.isBackoffice()).thenReturn(true);
    when(timereport.getSuborder().getInvoice()).thenReturn('Y');
    when(timereport.getSuborder().isInvoiceable()).thenReturn(true);

    assertBothAgree(true);
  }

  @Test
  void ruleOnTheOrderAloneCoversTheBookingsOfEverybody() {
    givenRule(CUSTOMER_ORDER_SIGN);

    assertBothAgree(true);
  }

  @Test
  void ruleOnTheSuborderCoversTheBookingsOfEverybodyThere() {
    givenRule(SUBORDER_SIGN);

    assertBothAgree(true);
  }

  @Test
  void compositeRuleCoversOnlyTheNamedEmployee() {
    givenRule(BOOKING_EMPLOYEE_SIGN + ":" + CUSTOMER_ORDER_SIGN);

    assertBothAgree(true);
  }

  @Test
  void compositeRuleForAnotherEmployeeDoesNotCoverThisBooking() {
    var other = new Employee();
    setField(other, "id", 42L);
    when(employeeService.getEmployeeBySign("yy")).thenReturn(other);
    givenRule("yy:" + CUSTOMER_ORDER_SIGN);

    assertBothAgree(false);
  }

  @Test
  void compositeRuleWithWildcardCoversTheEmployeeOnEveryOrder() {
    givenRule(BOOKING_EMPLOYEE_SIGN + ":*");

    assertBothAgree(true);
  }

  @Test
  void ruleOnAnotherOrderDoesNotCoverThisBooking() {
    var otherOrder = new Customerorder();
    setField(otherOrder, "id", 99L);
    when(customerorderService.getCustomerorderBySign("4711")).thenReturn(otherOrder);
    givenRule("4711");

    assertBothAgree(false);
  }

  /**
   * Two employee sets in two clauses must not be combined with the order set of a third one — a cross product would
   * grant the reader the bookings of the named employee on an order no rule mentions.
   */
  @Test
  void twoRulesStayTwoAlternatives() {
    var otherOrder = new Customerorder();
    setField(otherOrder, "id", 99L);
    when(customerorderService.getCustomerorderBySign("4711")).thenReturn(otherOrder);
    var otherEmployee = new Employee();
    setField(otherEmployee, "id", 42L);
    when(employeeService.getEmployeeBySign("yy")).thenReturn(otherEmployee);
    givenRules("yy:" + CUSTOMER_ORDER_SIGN, BOOKING_EMPLOYEE_SIGN + ":4711");

    assertBothAgree(false);
  }

  private void assertBothAgree(boolean expected) {
    var visibility = visibilityService.forPeriod(PERIOD);
    var authorized = timereportAuthorization.isAuthorized(timereport, READ);
    var covered = visibility.covers(timereport);

    assertThat(authorized)
        .describedAs("TimereportAuthorization.isAuthorized")
        .isEqualTo(expected);
    assertThat(covered)
        .describedAs("TimereportVisibility.covers must say the same as isAuthorized")
        .isEqualTo(authorized);
  }

  private void givenRule(String objectId) {
    givenRules(objectId);
  }

  private void givenRules(String... objectIds) {
    var rules = java.util.Arrays.stream(objectIds).map(objectId -> {
      var rule = new AuthorizationRule();
      rule.setCategory("TIMEREPORT");
      rule.setGranteeId(Set.of(READER));
      rule.setObjectId(Set.of(objectId));
      rule.setAccessLevels(Set.of(READ));
      rule.setValidFrom(of(2011, 1, 1));
      return rule;
    }).toList();
    when(authorizationRuleRepository.findAll()).thenReturn(rules);
  }
}
