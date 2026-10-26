package de.hbt.salat.dailyreport.auth;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.auth.domain.AccessLevel.READ;

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
import de.hbt.salat.auth.domain.AuthorizationRule;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.AuthorizationRuleRepository;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.service.CustomerorderService;

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
  /** The reader's login by id — what a rule names as its grantee (#1204). */
  private static final long READER_LOGIN_ID = 5L;
  private static final long READER_EMPLOYEE_ID = 1L;
  private static final String BOOKING_EMPLOYEE_SIGN = "xx";
  private static final long BOOKING_EMPLOYEE_ID = 2L;
  /** The contract the booking hangs on; the same person has an earlier one, {@link #EARLIER_CONTRACT_ID} (#1548). */
  private static final long BOOKING_CONTRACT_ID = 20L;
  private static final long EARLIER_CONTRACT_ID = 21L;
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
  private SalatUserRepository salatUserRepository;
  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private CustomerorderService customerorderService;
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
    when(employeecontractService.getTeamContractIdsIncludingExpired(anyLong())).thenReturn(Set.of());
    when(customerorderService.getIdsByResponsibleEmployeeId(anyLong())).thenReturn(List.of());
    when(authorizationRuleRepository.findAll()).thenReturn(List.of());
    var readerLogin = new SalatUser();
    setField(readerLogin, "id", READER_LOGIN_ID);
    readerLogin.setLoginname(READER);
    when(salatUserRepository.findAll()).thenReturn(List.of(readerLogin));

    var authService = new AuthService(authorizedUser, authorizationRuleRepository, salatUserRepository, salatProperties, null, null);
    authService.init();
    timereportAuthorization = new TimereportAuthorization(authorizedUser, authService, employeecontractService);
    visibilityService = new TimereportVisibilityService(authorizedUser, authorizedEmployee, authService,
        employeecontractService, customerorderService);

    // a booking of somebody else, so nothing but the rules can cover it
    when(timereport.getEmployeecontract().getId()).thenReturn(BOOKING_CONTRACT_ID);
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
  void peopleLeadSeesTheBookingsOnTheContractTheyLead() {
    when(authorizedUser.isPeopleLead()).thenReturn(true);
    when(timereport.getEmployeecontract().getSupervisors()).thenReturn(List.of(reader()));
    when(employeecontractService.getTeamContractIdsIncludingExpired(READER_EMPLOYEE_ID))
        .thenReturn(Set.of(BOOKING_CONTRACT_ID, EARLIER_CONTRACT_ID));

    assertBothAgree(true);
  }

  /**
   * A person has one id but, over time, several contracts (#1548). Whoever led one of them — here an earlier one, under
   * the lead of the reader — does not read the bookings on the others: the contract of this booking is led by somebody
   * else.
   */
  @Test
  void peopleLeadOfAnotherContractOfThePersonDoesNotSeeTheBooking() {
    when(authorizedUser.isPeopleLead()).thenReturn(true);
    when(timereport.getEmployeecontract().getSupervisors()).thenReturn(List.of(otherLead()));
    when(employeecontractService.getTeamContractIdsIncludingExpired(READER_EMPLOYEE_ID))
        .thenReturn(Set.of(EARLIER_CONTRACT_ID));

    assertBothAgree(false);
  }

  @Test
  void responsibleForTheOrderSeesWhatIsBookedOnIt() {
    var responsible = new Employee();
    setField(responsible, "id", READER_EMPLOYEE_ID);
    var salatUser = new SalatUser();
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
    givenRule("C" + CUSTOMER_ORDER_ID);

    assertBothAgree(true);
  }

  @Test
  void ruleOnTheSuborderCoversTheBookingsOfEverybodyThere() {
    givenRule("S" + SUBORDER_ID);

    assertBothAgree(true);
  }

  @Test
  void compositeRuleCoversOnlyTheNamedEmployee() {
    givenRule("E" + BOOKING_EMPLOYEE_ID + ":C" + CUSTOMER_ORDER_ID);

    assertBothAgree(true);
  }

  @Test
  void compositeRuleForAnotherEmployeeDoesNotCoverThisBooking() {
    givenRule("E42:C" + CUSTOMER_ORDER_ID);

    assertBothAgree(false);
  }

  @Test
  void compositeRuleWithWildcardCoversTheEmployeeOnEveryOrder() {
    givenRule("E" + BOOKING_EMPLOYEE_ID + ":*");

    assertBothAgree(true);
  }

  @Test
  void ruleOnAnotherOrderDoesNotCoverThisBooking() {
    givenRule("C99");

    assertBothAgree(false);
  }

  /**
   * Two employee sets in two clauses must not be combined with the order set of a third one — a cross product would
   * grant the reader the bookings of the named employee on an order no rule mentions.
   */
  @Test
  void twoRulesStayTwoAlternatives() {
    givenRules("E42:C" + CUSTOMER_ORDER_ID, "E" + BOOKING_EMPLOYEE_ID + ":C99");

    assertBothAgree(false);
  }

  /** A value the move to ids could not assign (#1204) grants nothing — in neither expression of the rule. */
  @Test
  void anUnresolvedValueCoversNothing() {
    givenRules("?" + BOOKING_EMPLOYEE_SIGN + ":" + CUSTOMER_ORDER_SIGN, "?" + CUSTOMER_ORDER_SIGN);

    assertBothAgree(false);
  }

  /** The old sign form would match the booking by its signs; after the move it must not grant anything any more. */
  @Test
  void theOldSignFormNoLongerCoversTheBooking() {
    givenRules(CUSTOMER_ORDER_SIGN, SUBORDER_SIGN, BOOKING_EMPLOYEE_SIGN + ":*");

    assertBothAgree(false);
  }

  /**
   * A rule counts for the bookings on the days it is valid (#1534). Its validity overlaps the period of the list, but
   * ends the day before the booking — the list used to show the booking anyway, and opening it then failed.
   */
  @Test
  void ruleEndedBeforeTheBookingDoesNotCoverIt() {
    givenRuleValid("C" + CUSTOMER_ORDER_ID, of(2011, 1, 1), of(2011, 1, 1));

    assertBothAgree(false);
  }

  @Test
  void ruleStartingAfterTheBookingDoesNotCoverIt() {
    givenRuleValid("E" + BOOKING_EMPLOYEE_ID + ":*", of(2011, 1, 3), null);

    assertBothAgree(false);
  }

  @Test
  void ruleValidOnTheBookingDayCoversIt() {
    givenRuleValid("S" + SUBORDER_ID, of(2011, 1, 2), of(2011, 1, 2));

    assertBothAgree(true);
  }

  /** The wildcard grants the category as a whole — but only on the days it is valid, like any other rule. */
  @Test
  void wildcardRuleEndedBeforeTheBookingDoesNotCoverIt() {
    givenRuleValid("*", of(2011, 1, 1), of(2011, 1, 1));

    assertBothAgree(false);
  }

  @Test
  void wildcardRuleValidOnTheBookingDayCoversIt() {
    givenRuleValid("*", of(2011, 1, 2), of(2011, 1, 2));

    assertBothAgree(true);
  }

  /** Without a period — what the order history asks (#1534) — a rule still counts only on its own days. */
  @Test
  void withoutAPeriodARuleCoversOnlyItsOwnDays() {
    givenRuleValid("C" + CUSTOMER_ORDER_ID, of(2011, 1, 1), of(2011, 1, 1));

    assertThat(visibilityService.anyTime().covers(timereport)).isFalse();
    assertThat(timereportAuthorization.isAuthorized(timereport, READ)).isFalse();
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

  private static Employee reader() {
    return employeeWithLogin(READER_EMPLOYEE_ID, READER);
  }

  private static Employee otherLead() {
    return employeeWithLogin(3L, "other-lead");
  }

  private static Employee employeeWithLogin(long id, String loginname) {
    var employee = new Employee();
    setField(employee, "id", id);
    var salatUser = new SalatUser();
    salatUser.setLoginname(loginname);
    employee.setSalatUser(salatUser);
    return employee;
  }

  private void givenRule(String objectId) {
    givenRules(objectId);
  }

  private void givenRuleValid(String objectId, LocalDate from, LocalDate until) {
    var rule = new AuthorizationRule();
    rule.setCategory("TIMEREPORT");
    rule.setGranteeId(Set.of(String.valueOf(READER_LOGIN_ID)));
    rule.setObjectId(Set.of(objectId));
    rule.setAccessLevels(Set.of(READ));
    rule.setValidFrom(from);
    rule.setValidUntil(until);
    when(authorizationRuleRepository.findAll()).thenReturn(List.of(rule));
  }

  private void givenRules(String... objectIds) {
    var rules = java.util.Arrays.stream(objectIds).map(objectId -> {
      var rule = new AuthorizationRule();
      rule.setCategory("TIMEREPORT");
      rule.setGranteeId(Set.of(String.valueOf(READER_LOGIN_ID)));
      rule.setObjectId(Set.of(objectId));
      rule.setAccessLevels(Set.of(READ));
      rule.setValidFrom(of(2011, 1, 1));
      return rule;
    }).toList();
    when(authorizationRuleRepository.findAll()).thenReturn(rules);
  }
}
