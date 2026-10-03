package de.hbt.salat.auth.service;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.auth.domain.AccessLevel.EXECUTE;
import static de.hbt.salat.auth.domain.AccessLevel.LOGIN;
import static de.hbt.salat.auth.domain.AccessLevel.READ;
import static de.hbt.salat.common.util.DateUtils.today;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.AuthorizationGranteeProvider;
import de.hbt.salat.auth.domain.AuthorizationObject;
import de.hbt.salat.auth.domain.AuthorizationRule;
import de.hbt.salat.auth.domain.AuthorizationRuleData;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.AuthorizationRuleRepository;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.common.SalatProperties;

/**
 * A rule written through the editor has to be read by the evaluation exactly like one entered by hand (#1074). The
 * two sides meet only in the database, so nothing but a round trip shows it: the service writes, and
 * {@link AuthService} is then asked with precisely the values the module's own checking site passes.
 */
@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = LENIENT)
class AuthorizationRuleRoundTripTest {

    /** The login asking, and its id — what a rule names as grantee since #1204. */
    private static final String LOGIN_NAME = "kr";
    private static final String GRANTEE = "5";

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
        when(authorizedUser.getLoginSign()).thenReturn(LOGIN_NAME);
        when(authorizedUser.getEffectiveLoginSign()).thenReturn(LOGIN_NAME);
        var login = new SalatUser();
        ReflectionTestUtils.setField(login, "id", Long.valueOf(GRANTEE));
        login.setLoginname(LOGIN_NAME);
        when(salatUserRepository.findAll()).thenReturn(List.of(login));

        authService = new AuthService(
            authorizedUser, authorizationRuleRepository, salatUserRepository, salatProperties, null, null);
        authService.init();
        ruleService = new AuthorizationRuleService(
            authorizationRuleRepository, List.of(), List.of(new AuthorizationGranteeProvider() {
                @Override
                public List<AuthorizationObject> granteeCandidates() {
                    return List.of(new AuthorizationObject(GRANTEE, LOGIN_NAME));
                }

                @Override
                public Map<String, AuthorizationObject> describe(Collection<String> granteeIds) {
                    return granteeIds.contains(GRANTEE) ? Map.of(GRANTEE, new AuthorizationObject(GRANTEE, LOGIN_NAME)) : Map.of();
                }
            }), authService, authorizedUser);
    }

    @Test
    void anEtlRuleFromTheEditorIsReadByTheEvaluation() {
        ruleService.create(rule("ETL", List.of("9"), EXECUTE));

        // exactly what ETLAuthorization asks with
        assertThat(authService.isAuthorized("ETL", today(), EXECUTE, "9")).isTrue();
        assertThat(authService.isAuthorized("ETL", today(), EXECUTE, "10")).isFalse();
    }

    @Test
    void anEmployeeRuleFromTheEditorIsReadByTheEvaluation() {
        ruleService.create(rule("EMPLOYEE", List.of("12"), LOGIN));

        // exactly what EmployeeAuthorization and switchLogin ask with - the id of the login that may be taken over,
        // asked for the real login, not the impersonated one
        assertThat(authService.isAuthorizedForOwnLogin("EMPLOYEE", today(), LOGIN, "12")).isTrue();
        assertThat(authService.isAuthorizedForOwnLogin("EMPLOYEE", today(), LOGIN, "13")).isFalse();
    }

    @Test
    void aTimereportRuleFromTheEditorIsReadByTheEvaluation() {
        ruleService.create(rule("TIMEREPORT", List.of("E2:C10"), READ));

        // exactly what TimereportAuthorization asks with, all five forms at once (TimereportRuleObject.formsOf)
        assertThat(authService.isAuthorized("TIMEREPORT", today(), READ,
            "C10", "S100", "E2:C10", "E2:S100", "E2:*")).isTrue();
        // the same order, another person: the composite is what keeps them apart
        assertThat(authService.isAuthorized("TIMEREPORT", today(), READ,
            "C10", "S100", "E3:C10", "E3:S100", "E3:*")).isFalse();
    }

    @Test
    void theNameComesBackAsItWasWrittenAndDoesNotTakePartInTheEvaluation() {
        ruleService.create(rule("ETL", List.of("9"), EXECUTE));

        assertThat(ruleService.getById(1L).name()).isEqualTo("Regel ETL");
        assertThat(ruleService.getAll()).singleElement().extracting(info -> info.name()).isEqualTo("Regel ETL");
        // the name is no object id: asking with it grants nothing
        assertThat(authService.isAuthorized("ETL", today(), EXECUTE, "Regel ETL")).isFalse();
        assertThat(authService.isAuthorized("ETL", today(), EXECUTE, "9")).isTrue();
    }

    @Test
    void aRuleFromBeforeNamesKeepsGrantingAndIsListedWithoutOne() {
        var legacy = new AuthorizationRule();
        legacy.setCategory("ETL");
        legacy.setGranteeId(Set.of(GRANTEE));
        legacy.setObjectId(Set.of("9"));
        legacy.setAccessLevels(Set.of(EXECUTE));
        legacy.setValidFrom(of(2011, 1, 1));
        authorizationRuleRepository.save(legacy); // as entered before #1168: no name

        assertThat(authService.isAuthorized("ETL", today(), EXECUTE, "9")).isTrue();
        assertThat(ruleService.getAll()).singleElement().extracting(info -> info.name()).isNull();
    }

    @Test
    void anEndedRuleStopsGranting() {
        ruleService.create(rule("ETL", List.of("9"), EXECUTE));
        assertThat(authService.isAuthorized("ETL", today(), EXECUTE, "9")).isTrue();

        ruleService.end(1L);

        assertThat(authService.isAuthorized("ETL", today().plusDays(1), EXECUTE, "9")).isFalse();
    }

    private AuthorizationRuleData rule(String category, List<String> objects, AccessLevel accessLevel) {
        return new AuthorizationRuleData(
            "Regel " + category, category, List.of(GRANTEE), objects, List.of(accessLevel), of(2011, 1, 1), null);
    }

}
