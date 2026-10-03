package de.hbt.salat.auth.service;

import static java.time.LocalDate.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.auth.domain.AccessLevel.READ;
import static de.hbt.salat.auth.domain.AccessLevel.WRITE;

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
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AuthorizationRule;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.AuthorizationRuleRepository;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.common.SalatProperties;

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
    private SalatUserRepository salatUserRepository;

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
        // a grantee is the id of a login (#1204); the logins as they are named today
        givenLogins(login(1L, "auth-sign"), login(2L, "login-sign"), login(3L, "impersonated-sign"),
            login(10L, "test-grantee1"), login(11L, "test-grantee2"));
        authService.init();
    }

    @Test
    void objectIdMatches() {
        // Arrange
        givenRules(ruleFor(Set.of("10", "11", "1"), Set.of("4444")));

        // Act & Assert
        assertTrue(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
    }

    @Test
    void anyObjectMatches() {
        // Arrange
        givenRules(ruleFor(Set.of("10", "11", "1"), Set.of("4444")));

        // Act & Assert
        assertTrue(authService.isAuthorizedAnyObject("TIMEREPORT", of(2011, 1, 2), READ));
    }

    @Test
    void emptyObjectIdMatchesEveryObject() {
        // Arrange
        givenRules(ruleFor(Set.of("10", "11", "1"), Set.of()));

        // Act & Assert
        assertTrue(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
    }

    @Test
    void clearCacheForcesReloadOnNextAccess() {
        // Arrange
        givenRules(ruleFor(Set.of("1"), Set.of()));

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
        givenRules(ruleFor(Set.of(ANY_MATCH, "10"), Set.of("4444")));

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
        givenRules(ruleFor(Set.of("2"), Set.of("4444")));

        // Act & Assert
        assertTrue(authService.isAuthorizedForOwnLogin("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
        assertFalse(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
    }

    /** A renamed login keeps its rules: the rule names the login by id, not by name (#1204). */
    @Test
    void aRenamedLoginKeepsItsRules() {
        givenRules(ruleFor(Set.of("1"), Set.of("4444")));
        givenLogins(login(1L, "auth-new"));
        when(authorizedUser.getEffectiveLoginSign()).thenReturn("auth-new");

        assertTrue(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
    }

    /**
     * After an anonymization the old login name is free again. Whoever gets it next is another login with another id
     * and inherits nothing — the very defect #966/#968 removed for the cost rates (#1204).
     */
    @Test
    void whoeverGetsAnOldLoginNameInheritsNoRule() {
        givenRules(ruleFor(Set.of("1"), Set.of("4444")));
        givenLogins(login(1L, "anonym-1"), login(7L, "auth-sign"));

        assertFalse(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
        assertFalse(authService.isAuthorizedAnyObject("TIMEREPORT", of(2011, 1, 2), READ));
    }

    /** A value the move to ids could not assign never matches, not even somebody whose login name it carries. */
    @Test
    void anUnresolvedGranteeMatchesNobody() {
        givenRules(ruleFor(Set.of("?auth-sign"), Set.of("4444")));

        assertFalse(authService.isAuthorized("TIMEREPORT", of(2011, 1, 2), READ, "4444"));
    }

    @Test
    void aGranteeIsShownByTheLoginNameItHasToday() {
        givenLogins(login(1L, "auth-new"));

        assertEquals("auth-new", authService.loginnameOf("1"));
        assertEquals(ANY_MATCH, authService.loginnameOf(ANY_MATCH));
        assertEquals("?alt", authService.loginnameOf("?alt"));
    }

    private void givenLogins(SalatUser... logins) {
        when(salatUserRepository.findAll()).thenReturn(List.of(logins));
        authService.clearCache();
    }

    private static SalatUser login(long id, String loginname) {
        var login = new SalatUser();
        ReflectionTestUtils.setField(login, "id", id);
        login.setLoginname(loginname);
        return login;
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
