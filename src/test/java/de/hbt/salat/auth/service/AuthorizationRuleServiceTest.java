package de.hbt.salat.auth.service;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.auth.domain.AccessLevel.READ;
import static de.hbt.salat.common.exception.ErrorCode.AR_NAME_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.AR_NAME_TAKEN;
import static de.hbt.salat.common.exception.ErrorCode.AR_NAME_TOO_LONG;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AuthorizationObject;
import de.hbt.salat.auth.domain.AuthorizationObjectProvider;
import de.hbt.salat.auth.domain.AuthorizationRule;
import de.hbt.salat.auth.domain.AuthorizationRuleData;
import de.hbt.salat.auth.domain.AuthorizationRuleInfo;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.ObjectJudgement;
import de.hbt.salat.auth.persistence.AuthorizationRuleRepository;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.DateUtils;

@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = LENIENT)
class AuthorizationRuleServiceTest {

    private static final String CATEGORY = "ETL";

    @Mock
    private AuthorizationRuleRepository authorizationRuleRepository;

    @Mock
    private AuthService authService;

    @Mock
    private AuthorizedUser authorizedUser;

    /** Records what the editor hands down, so that the wildcard can be shown never to arrive here. */
    private final List<String> judged = new ArrayList<>();

    private AuthorizationRuleService service;

    @BeforeEach
    void setUp() {
        when(authorizedUser.isManager()).thenReturn(true);
        when(authorizedUser.getLoginSign()).thenReturn("mgr");
        var provider = new AuthorizationObjectProvider() {
            @Override
            public String category() {
                return CATEGORY;
            }

            @Override
            public String labelKey() {
                return "main.auth.rule.category.etl";
            }

            @Override
            public String objectHintKey() {
                return "main.auth.rule.object.hint.etl";
            }

            @Override
            public List<AuthorizationObject> objects() {
                return List.of(new AuthorizationObject("umsatz", "umsatz"));
            }

            @Override
            public ObjectJudgement judge(String objectId) {
                judged.add(objectId);
                if (objectId.startsWith("!")) {
                    return ObjectJudgement.MALFORMED;
                }
                return AuthorizationObjectProvider.super.judge(objectId);
            }
        };
        service = new AuthorizationRuleService(
            authorizationRuleRepository, List.of(provider), List.of(() -> List.of("ar", "kr")), authService,
            authorizedUser);
    }

    @Test
    void aStoredRuleTakesEffectAtOnceInsteadOfAfterTheCacheExpires() {
        service.create(data(List.of("kr"), List.of("umsatz")));

        verify(authorizationRuleRepository).save(any(AuthorizationRule.class));
        verify(authService).clearCache();
    }

    @Test
    void anUnknownObjectIsSavedAndReported() {
        var unknown = service.create(data(List.of("kr"), List.of("umsatz", "kommt-noch")));

        assertThat(unknown).containsExactly("kommt-noch");
        verify(authorizationRuleRepository).save(any(AuthorizationRule.class));
    }

    @Test
    void aMalformedObjectIsRefusedAndNothingIsSaved() {
        assertThatThrownBy(() -> service.create(data(List.of("kr"), List.of("!nope"))))
            .isInstanceOf(InvalidDataException.class);

        verify(authorizationRuleRepository, never()).save(any(AuthorizationRule.class));
        verify(authService, never()).clearCache();
    }

    @Test
    void theWildcardIsThisModulesStatementAndIsNeverHandedToTheProvider() {
        var unknown = service.create(data(List.of("kr"), List.of("*")));

        assertThat(judged).as("the provider decides about its own objects, not about *").doesNotContain("*");
        assertThat(unknown).isEmpty();
        verify(authorizationRuleRepository).save(any(AuthorizationRule.class));
    }

    @Test
    void aRuleWithoutGranteeIsRefused() {
        // unlike an empty object, an empty grantee is not a wildcard - the rule would simply never fire
        assertThatThrownBy(() -> service.create(data(List.of(), List.of("umsatz"))))
            .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void aRuleWithoutCategoryOrAccessLevelIsRefused() {
        assertThatThrownBy(() -> service.create(
            new AuthorizationRuleData("Regel", null, List.of("kr"), List.of(), List.of(READ), null, null)))
            .isInstanceOf(InvalidDataException.class);
        assertThatThrownBy(() -> service.create(
            new AuthorizationRuleData("Regel", CATEGORY, List.of("kr"), List.of(), List.of(), null, null)))
            .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void aRuleThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> service.create(new AuthorizationRuleData(
            "Regel", CATEGORY, List.of("kr"), List.of("umsatz"), List.of(READ), of(2026, 2, 1), of(2026, 1, 1))))
            .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void aGranteeListLongerThanTheColumnIsRefused() {
        // the column would take the first 255 characters and drop the rest - a rule that looks complete and is not
        var many = IntStream.range(0, 100).mapToObj(i -> "sign" + i).toList();

        assertThatThrownBy(() -> service.create(data(many, List.of("umsatz"))))
            .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void aRuleWithoutANameIsRefusedAndNothingIsSaved() {
        for (var name : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> service.create(named(name)))
                .as("name <%s>", name)
                .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(AR_NAME_REQUIRED));
        }
        // an old rule gets its name the next time it is saved - it cannot be saved without one either
        assertThatThrownBy(() -> service.update(7L, named(" ")))
            .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(AR_NAME_REQUIRED));

        verify(authorizationRuleRepository, never()).save(any(AuthorizationRule.class));
    }

    @Test
    void aNameLongerThanTheColumnIsRefused() {
        assertThatThrownBy(() -> service.create(named("x".repeat(256))))
            .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(AR_NAME_TOO_LONG));
    }

    @Test
    void theNameIsStoredWithoutSurroundingBlanks() {
        var saved = new ArrayList<AuthorizationRule>();
        when(authorizationRuleRepository.save(any(AuthorizationRule.class))).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        service.create(named("  Umsatz-ETL  "));

        assertThat(saved).singleElement().extracting(AuthorizationRule::getName).isEqualTo("Umsatz-ETL");
    }

    @Test
    void aNameAnotherRuleCarriesIsRefusedRegardlessOfCase() {
        var other = storedRule(3L, "Umsatz-ETL");
        when(authorizationRuleRepository.findAllByNameIgnoreCase("UMSATZ-etl")).thenReturn(List.of(other));

        assertThatThrownBy(() -> service.create(named("UMSATZ-etl")))
            .isInstanceOfSatisfying(InvalidDataException.class, e -> {
                assertThat(errorCodeOf(e)).isEqualTo(AR_NAME_TAKEN);
                assertThat(e.getMessages().getFirst().getArguments()).containsExactly("UMSATZ-etl");
            });
        when(authorizationRuleRepository.findById(7L)).thenReturn(Optional.of(storedRule(7L, "Anders")));
        assertThatThrownBy(() -> service.update(7L, named("UMSATZ-etl")))
            .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(AR_NAME_TAKEN));

        verify(authorizationRuleRepository, never()).save(any(AuthorizationRule.class));
    }

    @Test
    void aRuleKeepingItsOwnNameIsNoConflict() {
        var rule = storedRule(3L, "Umsatz-ETL");
        when(authorizationRuleRepository.findById(3L)).thenReturn(Optional.of(rule));
        when(authorizationRuleRepository.findAllByNameIgnoreCase("umsatz-etl")).thenReturn(List.of(rule));

        service.update(3L, named("umsatz-etl"));

        assertThat(rule.getName()).isEqualTo("umsatz-etl");
        verify(authorizationRuleRepository).save(rule);
    }

    @Test
    void theListIsSortedByNameAndTheRulesWithoutOneComeLast() {
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(
            storedRule(1L, null), storedRule(2L, "zentrale Auswertung"), storedRule(3L, "Abnahme Team"),
            storedRule(4L, "buchungen lesen")));

        assertThat(service.getAll()).extracting(AuthorizationRuleInfo::name)
            .containsExactly("Abnahme Team", "buchungen lesen", "zentrale Auswertung", null);
    }

    @Test
    void endingKeepsTheRuleAndStopsItToday() {
        var rule = new AuthorizationRule();
        when(authorizationRuleRepository.findById(7L)).thenReturn(Optional.of(rule));

        service.end(7L);

        assertThat(rule.getValidUntil()).isEqualTo(DateUtils.today());
        verify(authorizationRuleRepository).save(rule);
        verify(authorizationRuleRepository, never()).delete(any());
        verify(authService).clearCache();
    }

    @Test
    void theGranteesOfferedAreTheOnesTheOwningModuleHandsOver() {
        // who is hidden is decided there, not here - auth may not even import the employee module
        assertThat(service.getGranteeCandidates()).containsExactly("ar", "kr");
    }

    @Test
    void withoutTheManagementNothingIsReadOrWritten() {
        when(authorizedUser.isManager()).thenReturn(false);

        assertThatThrownBy(() -> service.getAll()).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> service.create(data(List.of("kr"), List.of("umsatz"))))
            .isInstanceOf(AuthorizationException.class);
        verify(authorizationRuleRepository, never()).save(any(AuthorizationRule.class));
    }

    private AuthorizationRuleData data(List<String> grantees, List<String> objects) {
        return new AuthorizationRuleData("Regel", CATEGORY, grantees, objects, List.of(READ), of(2026, 1, 1), null);
    }

    private AuthorizationRuleData named(String name) {
        return new AuthorizationRuleData(name, CATEGORY, List.of("kr"), List.of("umsatz"), List.of(READ), null, null);
    }

    private static AuthorizationRule storedRule(long id, String name) {
        var rule = new AuthorizationRule();
        ReflectionTestUtils.setField(rule, "id", id);
        rule.setName(name);
        rule.setCategory(CATEGORY);
        rule.setGranteeId(Set.of("kr"));
        rule.setObjectId(Set.of("umsatz"));
        rule.setAccessLevels(Set.of(READ));
        return rule;
    }

    private static ErrorCode errorCodeOf(Throwable e) {
        assertThat(e).isInstanceOf(InvalidDataException.class);
        return ((InvalidDataException) e).getMessages().getFirst().getErrorCode();
    }

}
