package org.tb.auth.service;

import static java.time.LocalDate.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.tb.auth.domain.AccessLevel.READ;

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
import org.tb.auth.domain.AuthorizationRule;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizationRuleRepository;
import org.tb.common.SalatProperties;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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
        when(authorizedUser.getSign()).thenReturn("auth-sign");
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

}