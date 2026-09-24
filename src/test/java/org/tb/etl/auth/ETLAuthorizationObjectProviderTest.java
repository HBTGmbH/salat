package org.tb.etl.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.tb.auth.domain.AccessLevel.EXECUTE;
import static org.tb.auth.domain.ObjectJudgement.UNKNOWN;
import static org.tb.auth.domain.ObjectJudgement.VALID;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.tb.auth.domain.AuthorizationObject;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.etl.domain.ETLDefinition;
import org.tb.etl.domain.ETLDefinitionOption;
import org.tb.etl.service.ETLService;

@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = LENIENT)
class ETLAuthorizationObjectProviderTest {

    @Mock
    private ETLService etlService;

    @InjectMocks
    private ETLAuthorizationObjectProvider provider;

    @Test
    void theOfferedIdsAreTheDefinitionNames() {
        when(etlService.getExecutableDefinitions()).thenReturn(List.of(option("budget"), option("umsatz")));

        assertThat(provider.objects()).extracting(AuthorizationObject::id).containsExactly("budget", "umsatz");
    }

    @Test
    void aKnownNameIsValidAndAnUnknownOneIsOnlyNoted() {
        when(etlService.getExecutableDefinitions()).thenReturn(List.of(option("umsatz")));

        assertThat(provider.judge("umsatz")).isEqualTo(VALID);
        // a definition that arrives next week is a rule written ahead, not a typo to refuse
        assertThat(provider.judge("noch-nicht-da")).isEqualTo(UNKNOWN);
    }

    /** The id offered here has to be the value the checking site compares against, or the rule never fires. */
    @Test
    void theCheckingSiteAsksWithTheSameId() {
        when(etlService.getExecutableDefinitions()).thenReturn(List.of(option("umsatz")));
        var authorizedUser = mock(AuthorizedUser.class);
        var authService = mock(AuthService.class);

        new ETLAuthorization(authorizedUser, authService).isAuthorized(definition("umsatz"), EXECUTE);

        var objectIds = ArgumentCaptor.forClass(String[].class);
        verify(authService).isAuthorized(anyString(), any(), any(), objectIds.capture());
        assertThat(objectIds.getValue())
            .containsExactlyElementsOf(provider.objects().stream().map(AuthorizationObject::id).toList());
    }

    private ETLDefinitionOption option(String name) {
        return new ETLDefinitionOption(name, name);
    }

    private ETLDefinition definition(String name) {
        var definition = new ETLDefinition();
        definition.setName(name);
        return definition;
    }

}
