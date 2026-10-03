package de.hbt.salat.etl.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.auth.domain.AccessLevel.EXECUTE;
import static de.hbt.salat.auth.domain.ObjectJudgement.MALFORMED;
import static de.hbt.salat.auth.domain.ObjectJudgement.UNKNOWN;
import static de.hbt.salat.auth.domain.ObjectJudgement.VALID;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AuthorizationObject;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.etl.domain.ETLDefinition;
import de.hbt.salat.etl.domain.ETLDefinitionOption;
import de.hbt.salat.etl.service.ETLService;

@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = LENIENT)
class ETLAuthorizationObjectProviderTest {

    @Mock
    private ETLService etlService;

    @InjectMocks
    private ETLAuthorizationObjectProvider provider;

    /** The id, not the name (#1204): a renamed definition keeps its rules, a new one under the old name gets none. */
    @Test
    void theOfferedIdsAreTheDefinitionIdsNamedByTheirName() {
        when(etlService.getExecutableDefinitions()).thenReturn(List.of(option(7L, "budget"), option(9L, "umsatz")));

        assertThat(provider.objects()).extracting(AuthorizationObject::id).containsExactly("7", "9");
        assertThat(provider.objects()).extracting(AuthorizationObject::label).containsExactly("budget", "umsatz");
    }

    @Test
    void aKnownIdIsValidAnUnknownOneIsOnlyNotedAndANameIsMalformed() {
        when(etlService.getExecutableDefinitions()).thenReturn(List.of(option(9L, "umsatz")));

        assertThat(provider.judge("9")).isEqualTo(VALID);
        // a deleted definition leaves its id behind in the rule — readable, not refused
        assertThat(provider.judge("11")).isEqualTo(UNKNOWN);
        assertThat(provider.judge("umsatz")).isEqualTo(MALFORMED);
    }

    @Test
    void aStoredIdIsShownByTheNameTheDefinitionHasToday() {
        when(etlService.getExecutableDefinitions()).thenReturn(List.of(option(9L, "umsatz-neu")));

        assertThat(provider.describe(List.of("9", "11"))).containsOnlyKeys("9")
            .extractingByKey("9").extracting(AuthorizationObject::label).isEqualTo("umsatz-neu");
    }

    /** The id offered here has to be the value the checking site compares against, or the rule never fires. */
    @Test
    void theCheckingSiteAsksWithTheSameId() {
        when(etlService.getExecutableDefinitions()).thenReturn(List.of(option(9L, "umsatz")));
        var authorizedUser = mock(AuthorizedUser.class);
        var authService = mock(AuthService.class);

        new ETLAuthorization(authorizedUser, authService).isAuthorized(definition(9L, "umsatz"), EXECUTE);

        var objectIds = ArgumentCaptor.forClass(String[].class);
        verify(authService).isAuthorized(anyString(), any(), any(), objectIds.capture());
        assertThat(objectIds.getValue())
            .containsExactlyElementsOf(provider.objects().stream().map(AuthorizationObject::id).toList());
    }

    private ETLDefinitionOption option(long id, String name) {
        return new ETLDefinitionOption(id, name, name);
    }

    private ETLDefinition definition(long id, String name) {
        var definition = new ETLDefinition();
        ReflectionTestUtils.setField(definition, "id", id);
        definition.setName(name);
        return definition;
    }

}
