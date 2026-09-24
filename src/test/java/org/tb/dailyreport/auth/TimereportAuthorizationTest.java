package org.tb.dailyreport.auth;

import static java.time.LocalDate.of;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.tb.auth.domain.AccessLevel.READ;

import java.time.Duration;
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
import org.tb.common.SalatProperties;
import org.tb.dailyreport.domain.Timereport;

/**
 * A rule of the category TIMEREPORT may name whose bookings and on which order at once, both in the one object of a
 * rule. These tests run against the real {@link AuthService}, so the format the caller writes and the values the rule
 * engine compares cannot drift apart.
 */
@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = LENIENT)
class TimereportAuthorizationTest {

    private static final String READER = "reader";
    private static final String BOOKING_EMPLOYEE = "xx";
    private static final String CUSTOMER_ORDER_SIGN = "1453";
    private static final String SUBORDER_SIGN = "1453/01";
    private static final java.time.LocalDate BOOKING_DATE = of(2011, 1, 2);

    @Mock
    private AuthorizedUser authorizedUser;

    @Mock
    private AuthorizationRuleRepository authorizationRuleRepository;

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

        var authService = new AuthService(authorizedUser, authorizationRuleRepository, null, salatProperties, null, null);
        authService.init();
        timereportAuthorization = new TimereportAuthorization(authorizedUser, authService);

        // a booking of somebody else, so none of the checks before the rules applies
        when(timereport.getEmployeecontract().getEmployee().getSign()).thenReturn(BOOKING_EMPLOYEE);
        when(timereport.getEmployeecontract().getEmployee().getSalatUser().getLoginname()).thenReturn("somebody-else");
        when(timereport.getSuborder().getCustomerorder().getSign()).thenReturn(CUSTOMER_ORDER_SIGN);
        when(timereport.getSuborder().getCompleteOrderSign()).thenReturn(SUBORDER_SIGN);
        when(timereport.getSuborder().getCustomerorder().getResponsibleHbt()).thenReturn(List.of());
        when(timereport.getReferenceday().getRefdate()).thenReturn(BOOKING_DATE);
    }

    @Test
    void ruleOnTheOrderAloneCoversTheBookingsOfEverybody() {
        // Arrange
        givenRule(CUSTOMER_ORDER_SIGN);

        // Act & Assert
        assertTrue(timereportAuthorization.isAuthorized(timereport, READ));
    }

    @Test
    void ruleOnTheSuborderCoversTheBookingsOfEverybodyThere() {
        // Arrange
        givenRule(SUBORDER_SIGN);

        // Act & Assert
        assertTrue(timereportAuthorization.isAuthorized(timereport, READ));
    }

    @Test
    void compositeRuleCoversOnlyTheNamedEmployee() {
        // Arrange
        givenRule(BOOKING_EMPLOYEE + ":" + CUSTOMER_ORDER_SIGN);

        // Act & Assert
        assertTrue(timereportAuthorization.isAuthorized(timereport, READ));
    }

    @Test
    void compositeRuleForAnotherEmployeeDoesNotCoverThisBooking() {
        // Arrange - same order, other person
        givenRule("yy:" + CUSTOMER_ORDER_SIGN);

        // Act & Assert
        assertFalse(timereportAuthorization.isAuthorized(timereport, READ));
    }

    @Test
    void compositeRuleWithWildcardCoversTheEmployeeOnEveryOrder() {
        // Arrange
        givenRule(BOOKING_EMPLOYEE + ":*");

        // Act & Assert
        assertTrue(timereportAuthorization.isAuthorized(timereport, READ));
    }

    @Test
    void ruleOnAnotherOrderDoesNotCoverThisBooking() {
        // Arrange
        givenRule("4711");

        // Act & Assert
        assertFalse(timereportAuthorization.isAuthorized(timereport, READ));
    }

    private void givenRule(String objectId) {
        var rule = new AuthorizationRule();
        rule.setCategory("TIMEREPORT");
        rule.setGranteeId(Set.of(READER));
        rule.setObjectId(Set.of(objectId));
        rule.setAccessLevels(Set.of(READ));
        rule.setValidFrom(of(2011, 1, 1));
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(rule));
    }

}
