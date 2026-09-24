package org.tb.auth.service;

import static java.time.LocalDate.of;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.tb.auth.domain.AccessLevel.READ;
import static org.tb.auth.domain.AccessLevel.WRITE;

import java.time.Duration;
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
    void objectIdMatches() {
        // Arrange
        givenRules(ruleFor(Set.of("test-grantee1", "test-grantee2", "auth-sign"), Set.of("4444")));

        // Act & Assert
        assertTrue(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
    }

    @Test
    void anyObjectMatches() {
        // Arrange
        givenRules(ruleFor(Set.of("test-grantee1", "test-grantee2", "auth-sign"), Set.of("4444")));

        // Act & Assert
        assertTrue(authService.isAuthorizedAnyObject("TIMEREPORT", of(2011, 1, 2), READ));
    }

    @Test
    void emptyObjectIdMatchesEveryObject() {
        // Arrange
        givenRules(ruleFor(Set.of("test-grantee1", "test-grantee2", "auth-sign"), Set.of()));

        // Act & Assert
        assertTrue(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
    }

    @Test
    void clearCacheForcesReloadOnNextAccess() {
        // Arrange
        givenRules(ruleFor(Set.of("auth-sign"), Set.of()));

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
        givenRules(ruleFor(Set.of(ANY_MATCH), Set.of("4444")));

        // Act & Assert
        assertTrue(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
    }

    @Test
    void anyGranteeMatchesEverybodyOnAnyObjectCheck() {
        // Arrange
        givenRules(ruleFor(Set.of(ANY_MATCH), Set.of("4444")));

        // Act & Assert
        assertTrue(authService.isAuthorizedAnyObject("TIMEREPORT", of(2011, 1, 2), READ));
    }

    @Test
    void anyGranteeNextToConcreteGranteesMatchesEverybody() {
        // Arrange
        givenRules(ruleFor(Set.of(ANY_MATCH, "test-grantee1"), Set.of("4444")));

        // Act & Assert
        assertTrue(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
    }

    @Test
    void noGranteeMatchesNobody() {
        // Arrange - leaving the grantee out is not a wildcard, unlike leaving the object out
        givenRules(ruleFor(Set.of(), Set.of("4444")));

        // Act & Assert
        assertFalse(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
        assertFalse(authService.isAuthorizedAnyObject("TIMEREPORT", of(2011, 1, 2), READ));
    }

    @Test
    void anyGranteeStillObeysTheOtherConditions() {
        // Arrange
        var rule = ruleFor(Set.of(ANY_MATCH), Set.of("4444"));
        rule.setValidUntil(of(2011, 1, 1));
        givenRules(rule);

        // Act & Assert - outside the validity, another category, another access level, another object
        assertFalse(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
        assertTrue(authService.isAuthorized("TIMEREPORT", of(2011, 1, 1), READ, "4444"));
        assertFalse(authService.isAuthorized("REPORT_DEFINITION", of(2011, 1, 1), READ, "4444"));
        assertFalse(authService.isAuthorized("TIMEREPORT", of(2011, 1, 1), WRITE, "4444"));
        assertFalse(authService.isAuthorized("TIMEREPORT", of(2011, 1, 1), READ, "5555"));
    }

    @Test
    void ownLoginIsAskedForInsteadOfTheImpersonatedOne() {
        // Arrange - the user acts in the name of somebody else
        when(authorizedUser.getLoginSign()).thenReturn("login-sign");
        when(authorizedUser.getEffectiveLoginSign()).thenReturn("impersonated-sign");
        givenRules(ruleFor(Set.of("login-sign"), Set.of("4444")));

        // Act & Assert
        assertTrue(authService.isAuthorizedForOwnLogin("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
        assertFalse(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
    }

    private void givenRules(AuthorizationRule... rules) {
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(rules));
    }

    private AuthorizationRule ruleFor(Set<String> granteeIds, Set<String> objectIds) {
        var rule = new AuthorizationRule();
        rule.setValidFrom(of(2011, 1, 1));
        rule.setGranteeId(granteeIds);
        rule.setObjectId(objectIds);
        rule.setCategory("TIMEREPORT");
        rule.setAccessLevels(Set.of(READ));
        return rule;
    }

}
