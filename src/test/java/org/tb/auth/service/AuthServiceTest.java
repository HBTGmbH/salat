package org.tb.auth.service;

import static java.time.LocalDate.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.tb.auth.domain.AccessLevel.READ;
import static org.tb.auth.domain.AccessLevel.WRITE;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.tb.auth.domain.AuthorizationRule;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizationRuleRepository;
import org.tb.common.SalatProperties;

@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = LENIENT)
class AuthServiceTest {

    /** the wildcard as it is written into the database */
    private static final String ANY_MATCH = "*";

    @Mock
    private AuthorizedUser authorizedUser;

    @Mock
    private AuthorizationRuleRepository authorizationRuleRepository;

    @Mock
    private SalatProperties salatProperties;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        var authServiceProps = new SalatProperties.AuthService();
        authServiceProps.setCacheExpiry(Duration.ofMillis(1000));
        when(salatProperties.getAuthService()).thenReturn(authServiceProps);
        when(authorizedUser.getLoginSign()).thenReturn("auth-sign");
        when(authorizedUser.getEffectiveLoginSign()).thenReturn("auth-sign");
        authService.init();
    }

    @Test
    void testObjectId() {
        // Arrange
        var rule = new AuthorizationRule();
        rule.setObjectId(new HashSet<>());
        rule.setValidFrom(of(2011, 1, 1));
        rule.setGrantorId("test-grantor");
        rule.setGranteeId(Set.of("test-grantee1", "test-grantee2", "auth-sign"));
        rule.setCategory("TIMEREPORT");
        rule.setAccessLevels(Set.of(READ));
        rule.setObjectId(Set.of("4444"));
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(
            rule
        ));

        // Act
        var authorized = authService.isAuthorized(
            "test-grantor",
            "TIMEREPORT",
            of(2011, 1, 2),
            READ,
            "4444"
        );

        // Assert
        assertEquals(true, authorized); // Assuming no matches in this case
    }

    @Test
    void testAnyObject() {
        // Arrange
        var rule = new AuthorizationRule();
        rule.setObjectId(new HashSet<>());
        rule.setValidFrom(of(2011, 1, 1));
        rule.setGrantorId("test-grantor");
        rule.setGranteeId(Set.of("test-grantee1", "test-grantee2", "auth-sign"));
        rule.setCategory("TIMEREPORT");
        rule.setAccessLevels(Set.of(READ));
        rule.setObjectId(Set.of("4444"));
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(
            rule
        ));

        // Act
        var authorized = authService.isAuthorizedAnyObject(
            "test-grantor",
            "TIMEREPORT",
            of(2011, 1, 2),
            READ
        );

        // Assert
        assertEquals(true, authorized); // Assuming no matches in this case
    }

    @Test
    void testEmptyObjectId() {
        // Arrange
        var rule = new AuthorizationRule();
        rule.setObjectId(new HashSet<>());
        rule.setValidFrom(of(2011, 1, 1));
        rule.setGrantorId("test-grantor");
        rule.setGranteeId(Set.of("test-grantee1", "test-grantee2", "auth-sign"));
        rule.setCategory("TIMEREPORT");
        rule.setAccessLevels(Set.of(READ));
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(
            rule
        ));

        // Act
        var authorized = authService.isAuthorized(
            "test-grantor",
            "TIMEREPORT",
            of(2011, 1, 2),
            READ,
            "4444"
        );

        // Assert
        assertEquals(true, authorized); // Assuming no matches in this case
    }

    @Test
    void clearCacheForcesReloadOnNextAccess() {
        // Arrange
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(newRule()));

        // Act
        authService.isAuthorizedAnyObject("TIMEREPORT", of(2011, 1, 2), READ);
        authService.isAuthorizedAnyObject("TIMEREPORT", of(2011, 1, 2), READ);

        // Assert - the second access is served from the cache, which has not expired yet
        verify(authorizationRuleRepository, times(1)).findAll();

        // Act - clearing the cache makes the next access reload, without waiting for the expiry
        authService.clearCache();
        authService.isAuthorizedAnyObject("TIMEREPORT", of(2011, 1, 2), READ);

        // Assert
        verify(authorizationRuleRepository, times(2)).findAll();
    }

    @Test
    void anyGranteeMatchesEverybodyOnObjectCheck() {
        // Arrange - the rule names nobody in particular, and "auth-sign" is not among its grantees
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(ruleForGrantees(Set.of(ANY_MATCH))));

        // Act & Assert
        assertTrue(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
    }

    @Test
    void anyGranteeMatchesEverybodyOnObjectCheckWithGrantor() {
        // Arrange
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(ruleForGrantees(Set.of(ANY_MATCH))));

        // Act & Assert
        assertTrue(authService.isAuthorized("test-grantor", "TIMEREPORT", of(2011, 1, 2), READ, "4444"));
    }

    @Test
    void anyGranteeMatchesEverybodyOnAnyObjectCheck() {
        // Arrange
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(ruleForGrantees(Set.of(ANY_MATCH))));

        // Act & Assert
        assertTrue(authService.isAuthorizedAnyObject("TIMEREPORT", of(2011, 1, 2), READ));
    }

    @Test
    void anyGranteeMatchesEverybodyOnAnyObjectCheckWithGrantor() {
        // Arrange
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(ruleForGrantees(Set.of(ANY_MATCH))));

        // Act & Assert
        assertTrue(authService.isAuthorizedAnyObject("test-grantor", "TIMEREPORT", of(2011, 1, 2), READ));
    }

    @Test
    void anyGranteeNextToConcreteGranteesMatchesEverybody() {
        // Arrange
        when(authorizationRuleRepository.findAll()).thenReturn(
            List.of(ruleForGrantees(Set.of(ANY_MATCH, "test-grantee1")))
        );

        // Act & Assert
        assertTrue(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
    }

    @Test
    void noGranteeMatchesNobody() {
        // Arrange - leaving the grantee out is not a wildcard, unlike leaving the object out
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(ruleForGrantees(Set.of())));

        // Act & Assert
        assertFalse(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
        assertFalse(authService.isAuthorizedAnyObject("TIMEREPORT", of(2011, 1, 2), READ));
        assertFalse(authService.isAuthorizedAnyObject("test-grantor", "TIMEREPORT", of(2011, 1, 2), READ));
    }

    @Test
    void anyGranteeStillObeysTheOtherConditions() {
        // Arrange
        var rule = ruleForGrantees(Set.of(ANY_MATCH));
        rule.setValidUntil(of(2011, 1, 1));
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(rule));

        // Act & Assert - outside the validity, another category, another access level, another object
        assertFalse(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
        assertTrue(authService.isAuthorized("TIMEREPORT", of(2011, 1, 1), READ, "4444"));
        assertFalse(authService.isAuthorized("REPORT_DEFINITION", of(2011, 1, 1), READ, "4444"));
        assertFalse(authService.isAuthorized("TIMEREPORT", of(2011, 1, 1), WRITE, "4444"));
        assertFalse(authService.isAuthorized("TIMEREPORT", of(2011, 1, 1), READ, "5555"));
    }

    @Test
    void useLoginSignAsksForTheOwnLoginNotForTheImpersonatedOne() {
        // Arrange - the user acts in the name of somebody else
        when(authorizedUser.getLoginSign()).thenReturn("login-sign");
        when(authorizedUser.getEffectiveLoginSign()).thenReturn("impersonated-sign");
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(ruleForGrantees(Set.of("login-sign"))));

        // Act & Assert
        assertTrue(authService.isAuthorizedAnyObject("test-grantor", "TIMEREPORT", of(2011, 1, 2), READ, true));
        assertFalse(authService.isAuthorizedAnyObject("test-grantor", "TIMEREPORT", of(2011, 1, 2), READ, false));
    }

    private AuthorizationRule ruleForGrantees(Set<String> granteeIds) {
        var rule = newRule();
        rule.setGranteeId(granteeIds);
        rule.setObjectId(Set.of("4444"));
        return rule;
    }

    private AuthorizationRule newRule() {
        var rule = new AuthorizationRule();
        rule.setObjectId(new HashSet<>());
        rule.setValidFrom(of(2011, 1, 1));
        rule.setGrantorId("test-grantor");
        rule.setGranteeId(Set.of("auth-sign"));
        rule.setCategory("TIMEREPORT");
        rule.setAccessLevels(Set.of(READ));
        return rule;
    }

}