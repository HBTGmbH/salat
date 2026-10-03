package de.hbt.salat.dailyreport.auth;

import static org.mockito.Mockito.mock;
import static java.time.LocalDate.of;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.auth.domain.AccessLevel.READ;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AuthorizationRule;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.AuthorizationRuleRepository;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.employee.service.EmployeecontractService;

/**
 * A rule of the category TIMEREPORT may name whose bookings and on which order at once, both in the one object of a
 * rule. These tests run against the real {@link AuthService}, so the format the caller writes and the values the rule
 * engine compares cannot drift apart.
 */
@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = LENIENT)
class TimereportAuthorizationTest {

    private static final String READER = "reader";
    private static final long READER_LOGIN_ID = 5L;
    private static final String BOOKING_EMPLOYEE = "xx";
    private static final long BOOKING_EMPLOYEE_ID = 2L;
    private static final long CUSTOMER_ORDER_ID = 10L;
    private static final long SUBORDER_ID = 100L;
    private static final java.time.LocalDate BOOKING_DATE = of(2011, 1, 2);

    @Mock
    private AuthorizedUser authorizedUser;

    @Mock
    private AuthorizationRuleRepository authorizationRuleRepository;

    @Mock
    private SalatUserRepository salatUserRepository;

    @Mock
    private SalatProperties salatProperties;

    @Mock(answer = RETURNS_DEEP_STUBS)
    private Timereport timereport;

    private TimereportAuthorization timereportAuthorization;

    @BeforeEach
    void setUp() {
        var authServiceProps = new SalatProperties.AuthService();
        authServiceProps.setCacheExpiry(Duration.ofMillis(1000));
        when(salatProperties.getAuthService()).thenReturn(authServiceProps);
        when(authorizedUser.getLoginSign()).thenReturn(READER);
        when(authorizedUser.getEffectiveLoginSign()).thenReturn(READER);
        var readerLogin = new SalatUser();
        ReflectionTestUtils.setField(readerLogin, "id", READER_LOGIN_ID);
        readerLogin.setLoginname(READER);
        when(salatUserRepository.findAll()).thenReturn(List.of(readerLogin));

        var authService = new AuthService(authorizedUser, authorizationRuleRepository, salatUserRepository, salatProperties, null, null);
        authService.init();
        timereportAuthorization = new TimereportAuthorization(authorizedUser, authService, mock(EmployeecontractService.class));

        // a booking of somebody else, so none of the checks before the rules applies
        when(timereport.getEmployeecontract().getEmployee().getId()).thenReturn(BOOKING_EMPLOYEE_ID);
        when(timereport.getEmployeecontract().getEmployee().getSign()).thenReturn(BOOKING_EMPLOYEE);
        when(timereport.getEmployeecontract().getEmployee().getSalatUser().getLoginname()).thenReturn("somebody-else");
        when(timereport.getSuborder().getCustomerorder().getId()).thenReturn(CUSTOMER_ORDER_ID);
        when(timereport.getSuborder().getId()).thenReturn(SUBORDER_ID);
        when(timereport.getSuborder().getCustomerorder().getResponsibleHbt()).thenReturn(List.of());
        when(timereport.getReferenceday().getRefdate()).thenReturn(BOOKING_DATE);
    }

    @Test
    void ruleOnTheOrderAloneCoversTheBookingsOfEverybody() {
        // Arrange
        givenRule("C" + CUSTOMER_ORDER_ID);

        // Act & Assert
        assertTrue(timereportAuthorization.isAuthorized(timereport, READ));
    }

    @Test
    void ruleOnTheSuborderCoversTheBookingsOfEverybodyThere() {
        // Arrange
        givenRule("S" + SUBORDER_ID);

        // Act & Assert
        assertTrue(timereportAuthorization.isAuthorized(timereport, READ));
    }

    @Test
    void compositeRuleCoversOnlyTheNamedEmployee() {
        // Arrange
        givenRule("E" + BOOKING_EMPLOYEE_ID + ":C" + CUSTOMER_ORDER_ID);

        // Act & Assert
        assertTrue(timereportAuthorization.isAuthorized(timereport, READ));
    }

    @Test
    void compositeRuleForAnotherEmployeeDoesNotCoverThisBooking() {
        // Arrange - same order, other person
        givenRule("E3:C" + CUSTOMER_ORDER_ID);

        // Act & Assert
        assertFalse(timereportAuthorization.isAuthorized(timereport, READ));
    }

    @Test
    void compositeRuleWithWildcardCoversTheEmployeeOnEveryOrder() {
        // Arrange
        givenRule("E" + BOOKING_EMPLOYEE_ID + ":*");

        // Act & Assert
        assertTrue(timereportAuthorization.isAuthorized(timereport, READ));
    }

    @Test
    void ruleOnAnotherOrderDoesNotCoverThisBooking() {
        // Arrange
        givenRule("C4711");

        // Act & Assert
        assertFalse(timereportAuthorization.isAuthorized(timereport, READ));
    }

    /** The rule names the person by id (#1204): a changed sign changes nothing. */
    @Test
    void aRuleKeepsCoveringAPersonWhoseSignChanged() {
        givenRule("E" + BOOKING_EMPLOYEE_ID + ":*");
        when(timereport.getEmployeecontract().getEmployee().getSign()).thenReturn("neu");

        assertTrue(timereportAuthorization.isAuthorized(timereport, READ));
    }

    /** Whoever gets the old sign next is another person with another id and inherits nothing (#1204). */
    @Test
    void anotherPersonWithTheOldSignIsNotCovered() {
        givenRule("E" + BOOKING_EMPLOYEE_ID + ":*");
        when(timereport.getEmployeecontract().getEmployee().getId()).thenReturn(3L);

        assertFalse(timereportAuthorization.isAuthorized(timereport, READ));
    }

    private void givenRule(String objectId) {
        var rule = new AuthorizationRule();
        rule.setCategory("TIMEREPORT");
        rule.setGranteeId(Set.of(String.valueOf(READER_LOGIN_ID)));
        rule.setObjectId(Set.of(objectId));
        rule.setAccessLevels(Set.of(READ));
        rule.setValidFrom(of(2011, 1, 1));
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(rule));
    }

}
