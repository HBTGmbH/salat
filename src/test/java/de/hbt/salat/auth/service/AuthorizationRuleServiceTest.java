package de.hbt.salat.auth.service;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.auth.domain.AccessLevel.READ;
import static de.hbt.salat.common.exception.ErrorCode.AR_GRANTEE_UNKNOWN;
import static de.hbt.salat.common.exception.ErrorCode.AR_NAME_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.AR_NAME_TAKEN;
import static de.hbt.salat.common.exception.ErrorCode.AR_NAME_TOO_LONG;
import static de.hbt.salat.common.exception.ErrorCode.AR_OBJECT_UNRESOLVED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import de.hbt.salat.auth.domain.AuthorizationGranteeProvider;
import de.hbt.salat.auth.domain.AuthorizationObject;
import de.hbt.salat.auth.domain.AuthorizationObjectProvider;
import de.hbt.salat.auth.domain.AuthorizationRule;
import de.hbt.salat.auth.domain.AuthorizationRuleData;
import de.hbt.salat.auth.domain.AuthorizationRuleInfo;
import de.hbt.salat.auth.domain.AuthorizationRuleValue;
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

    /** A category whose objects cannot be enumerated: typed as signs, stored as ids (#1204). */
    private static final String TYPED_CATEGORY = "TIMEREPORT";

    /** The logins the grantee provider knows; {@code hidden} only by describing, as a hidden person would be. */
    private static final List<AuthorizationObject> GRANTEES = List.of(
        new AuthorizationObject("kr", "Klara Rot | kr"), new AuthorizationObject("ar", "Anton Rot | ar"),
        new AuthorizationObject("kr", "Klara Rot | kr"));
    private static final AuthorizationObject HIDDEN = new AuthorizationObject("hd", "Hanna Dunkel | hd");

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
        var typedProvider = new AuthorizationObjectProvider() {
            @Override
            public String category() {
                return TYPED_CATEGORY;
            }

            @Override
            public String labelKey() {
                return "main.auth.rule.category.timereport";
            }

            @Override
            public String objectHintKey() {
                return "main.auth.rule.object.hint.timereport";
            }

            @Override
            public List<AuthorizationObject> objects() {
                return List.of();
            }

            @Override
            public Optional<String> objectIdOf(String input) {
                return "xx:1453".equals(input) ? Optional.of("E12:C42") : Optional.empty();
            }

            @Override
            public Map<String, AuthorizationObject> describe(Collection<String> objectIds) {
                return objectIds.contains("E12:C42")
                    ? Map.of("E12:C42", new AuthorizationObject("E12:C42", "xx:1453"))
                    : Map.of();
            }

            @Override
            public ObjectJudgement judge(String objectId) {
                return "E12:C42".equals(objectId) ? ObjectJudgement.VALID : ObjectJudgement.UNKNOWN;
            }
        };
        var granteeProvider = new AuthorizationGranteeProvider() {
            @Override
            public List<AuthorizationObject> granteeCandidates() {
                return GRANTEES;
            }

            @Override
            public Map<String, AuthorizationObject> describe(Collection<String> granteeIds) {
                var described = new HashMap<String, AuthorizationObject>();
                GRANTEES.stream().filter(g -> granteeIds.contains(g.id())).forEach(g -> described.put(g.id(), g));
                IntStream.range(0, 100).mapToObj(i -> "sign" + i).filter(granteeIds::contains)
                    .forEach(id -> described.put(id, new AuthorizationObject(id, id)));
                if (granteeIds.contains(HIDDEN.id())) described.put(HIDDEN.id(), HIDDEN);
                return described;
            }
        };
        service = new AuthorizationRuleService(
            authorizationRuleRepository, List.of(provider, typedProvider), List.of(granteeProvider), authService,
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
        when(authorizationRuleRepository.findById(7L)).thenReturn(Optional.of(storedRule(7L, null)));
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
        // one entry per login, in the order the owning module hands them over, named the way it names them
        assertThat(service.getGranteeCandidates(List.of())).containsExactly(
            new AuthorizationRuleValue("kr", "Klara Rot | kr", null, false),
            new AuthorizationRuleValue("ar", "Anton Rot | ar", null, false));
    }

    @Test
    void aGranteeTheRuleKeepsIsOfferedByTheRecordItNamesEvenWhenHiddenOrUnresolved() {
        assertThat(service.getGranteeCandidates(List.of("kr", "hd", "?alt", "77")))
            .extracting(AuthorizationRuleValue::id, AuthorizationRuleValue::label, AuthorizationRuleValue::unresolved)
            .containsExactly(
                tuple("kr", "Klara Rot | kr", false),
                tuple("ar", "Anton Rot | ar", false),
                tuple("hd", "Hanna Dunkel | hd", false),
                tuple("?alt", "alt", true),
                tuple("77", "77", true));
    }

    /** A grantee is a login by id (#1204); a value no login answers to would be a rule that never fires. */
    @Test
    void anUnknownGranteeIsRefusedAndNothingIsSaved() {
        assertThatThrownBy(() -> service.create(data(List.of("kr", "niemand"), List.of("umsatz"))))
            .isInstanceOfSatisfying(InvalidDataException.class, e -> {
                assertThat(errorCodeOf(e)).isEqualTo(AR_GRANTEE_UNKNOWN);
                assertThat(e.getMessages().getFirst().getArguments()).containsExactly("niemand");
            });

        verify(authorizationRuleRepository, never()).save(any(AuthorizationRule.class));
    }

    /** Editing a migrated rule must not fail on a value the migration could not assign and nobody touched. */
    @Test
    void anUnresolvedGranteeTheRuleCarriesAlreadyIsKeptOnEdit() {
        var rule = storedRule(7L, "Regel");
        rule.setGranteeId(new HashSet<>(Set.of("kr", "?alt")));
        when(authorizationRuleRepository.findById(7L)).thenReturn(Optional.of(rule));

        service.update(7L, data(List.of("kr", "?alt"), List.of("umsatz")));

        assertThat(rule.getGranteeId()).containsExactlyInAnyOrder("kr", "?alt");
        verify(authorizationRuleRepository).save(rule);
    }

    @Test
    void whatWasTypedIntoAFreeTextCategoryIsStoredAsTheIdTheModuleTranslatesItTo() {
        var saved = new ArrayList<AuthorizationRule>();
        when(authorizationRuleRepository.save(any(AuthorizationRule.class))).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        service.create(typed(List.of("xx:1453", "*")));

        assertThat(saved).singleElement().extracting(AuthorizationRule::getObjectId)
            .isEqualTo(Set.of("E12:C42", "*"));
    }

    @Test
    void aTypedValueThatNamesNoRecordIsRefused() {
        assertThatThrownBy(() -> service.create(typed(List.of("yy:9999"))))
            .isInstanceOfSatisfying(InvalidDataException.class, e -> {
                assertThat(errorCodeOf(e)).isEqualTo(AR_OBJECT_UNRESOLVED);
                assertThat(e.getMessages().getFirst().getArguments()).containsExactly("yy:9999");
            });

        verify(authorizationRuleRepository, never()).save(any(AuthorizationRule.class));
    }

    /** The form hands stored values back as they are stored; they are not typed input and stay untranslated. */
    @Test
    void aStoredValueOfAFreeTextCategoryComesBackFromTheFormUnchanged() {
        var rule = storedRule(7L, "Regel");
        rule.setCategory(TYPED_CATEGORY);
        rule.setObjectId(new HashSet<>(Set.of("E12:C42", "?xx:alt")));
        when(authorizationRuleRepository.findById(7L)).thenReturn(Optional.of(rule));

        service.update(7L, typed(List.of("E12:C42", "?xx:alt")));

        assertThat(rule.getObjectId()).containsExactlyInAnyOrder("E12:C42", "?xx:alt");
    }

    @Test
    void theListShowsTheRecordsTheRuleNamesAndMarksWhatNoRecordAnswersTo() {
        var rule = storedRule(1L, "Regel");
        rule.setGranteeId(Set.of("kr", "?alt", "*"));
        rule.setCategory(TYPED_CATEGORY);
        rule.setObjectId(Set.of("E12:C42", "E99:*"));
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(rule));

        var info = service.getAll().getFirst();

        assertThat(info.grantees())
            .extracting(AuthorizationRuleValue::shown, AuthorizationRuleValue::unresolved)
            .containsExactly(
                tuple("*", false),
                tuple("alt", true),
                tuple("Klara Rot | kr", false));
        assertThat(info.objects())
            .extracting(AuthorizationRuleValue::shown, AuthorizationRuleValue::unresolved)
            .containsExactly(
                tuple("E99:*", true),
                tuple("xx:1453", false));
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

    private AuthorizationRuleData typed(List<String> objects) {
        return new AuthorizationRuleData("Regel", TYPED_CATEGORY, List.of("kr"), objects, List.of(READ), null, null);
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
