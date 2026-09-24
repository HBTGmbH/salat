package org.tb.auth.service;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.tb.auth.domain.AccessLevel.EXECUTE;
import static org.tb.auth.domain.AccessLevel.LOGIN;
import static org.tb.auth.domain.AccessLevel.READ;
import static org.tb.common.util.DateUtils.today;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizationRule;
import org.tb.auth.domain.AuthorizationRuleData;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizationRuleRepository;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.SalatProperties;

/**
 * A rule written through the editor has to be read by the evaluation exactly like one entered by hand (#1074). The
 * two sides meet only in the database, so nothing but a round trip shows it: the service writes, and
 * {@link AuthService} is then asked with precisely the values the module's own checking site passes.
 */
@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = LENIENT)
class AuthorizationRuleRoundTripTest {

    private static final String GRANTEE = "kr";

    @Mock
    private AuthorizationRuleRepository authorizationRuleRepository;

    @Mock
    private SalatUserRepository salatUserRepository;

    @Mock
    private SalatProperties salatProperties;

    @Mock
    private AuthorizedUser authorizedUser;

    private AuthService authService;
    private AuthorizationRuleService ruleService;

    @BeforeEach
    void setUp() {
        var stored = new ArrayList<AuthorizationRule>();
        when(authorizationRuleRepository.save(any(AuthorizationRule.class))).thenAnswer(invocation -> {
            stored.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(authorizationRuleRepository.findAll()).thenReturn(stored); // the live list: what was saved is what is read
        when(authorizationRuleRepository.findById(any())).thenAnswer(invocation -> stored.stream().findFirst());

        var authServiceProps = new SalatProperties.AuthService();
        authServiceProps.setCacheExpiry(Duration.ofMillis(1000));
        when(salatProperties.getAuthService()).thenReturn(authServiceProps);
        when(authorizedUser.isManager()).thenReturn(true);
        when(authorizedUser.getLoginSign()).thenReturn(GRANTEE);
        when(authorizedUser.getEffectiveLoginSign()).thenReturn(GRANTEE);

        authService = new AuthService(
            authorizedUser, authorizationRuleRepository, salatUserRepository, salatProperties, null, null);
        authService.init();
        ruleService = new AuthorizationRuleService(
            authorizationRuleRepository, List.of(), List.of(), authService, authorizedUser);
    }

    @Test
    void anEtlRuleFromTheEditorIsReadByTheEvaluation() {
        ruleService.create(rule("ETL", List.of("umsatz"), EXECUTE));

        // exactly what ETLAuthorization asks with
        assertThat(authService.isAuthorized("ETL", today(), EXECUTE, "umsatz")).isTrue();
        assertThat(authService.isAuthorized("ETL", today(), EXECUTE, "anderer-lauf")).isFalse();
    }

    @Test
    void anEmployeeRuleFromTheEditorIsReadByTheEvaluation() {
        ruleService.create(rule("EMPLOYEE", List.of("l.muster"), LOGIN));

        // exactly what EmployeeAuthorization and switchLogin ask with - the real login, not the impersonated one
        assertThat(authService.isAuthorizedForOwnLogin("EMPLOYEE", today(), LOGIN, "l.muster")).isTrue();
        assertThat(authService.isAuthorizedForOwnLogin("EMPLOYEE", today(), LOGIN, "jemand.anders")).isFalse();
    }

    @Test
    void aTimereportRuleFromTheEditorIsReadByTheEvaluation() {
        ruleService.create(rule("TIMEREPORT", List.of("xx:1453"), READ));

        // exactly what TimereportAuthorization asks with, all five forms at once
        assertThat(authService.isAuthorized("TIMEREPORT", today(), READ,
            "1453", "1453/01", "xx:1453", "xx:1453/01", "xx:*")).isTrue();
        // the same order, another person: the composite is what keeps them apart
        assertThat(authService.isAuthorized("TIMEREPORT", today(), READ,
            "1453", "1453/01", "yy:1453", "yy:1453/01", "yy:*")).isFalse();
    }

    @Test
    void anEndedRuleStopsGranting() {
        ruleService.create(rule("ETL", List.of("umsatz"), EXECUTE));
        assertThat(authService.isAuthorized("ETL", today(), EXECUTE, "umsatz")).isTrue();

        ruleService.end(1L);

        assertThat(authService.isAuthorized("ETL", today().plusDays(1), EXECUTE, "umsatz")).isFalse();
    }

    private AuthorizationRuleData rule(String category, List<String> objects, AccessLevel accessLevel) {
        return new AuthorizationRuleData(
            category, List.of(GRANTEE), objects, List.of(accessLevel), of(2011, 1, 1), null);
    }

}
