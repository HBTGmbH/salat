package org.tb.reporting.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.tb.auth.domain.AccessLevel.EXECUTE;
import static org.tb.auth.domain.ObjectJudgement.MALFORMED;
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
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.service.ReportService;

@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = LENIENT)
class ReportAuthorizationObjectProviderTest {

    @Mock
    private ReportService reportService;

    @InjectMocks
    private ReportAuthorizationObjectProvider provider;

    @Test
    void theOfferedIdIsTheDatabaseIdAndTheLabelIsTheName() {
        var umsatz = report(42L, "Umsatz je Auftrag");
        when(reportService.getReportDefinitions()).thenReturn(List.of(umsatz));

        assertThat(provider.objects())
            .containsExactly(new AuthorizationObject("42", "Umsatz je Auftrag"));
    }

    @Test
    void somethingThatIsNotANumberCannotBeAReportId() {
        var umsatz = report(42L, "Umsatz je Auftrag");
        when(reportService.getReportDefinitions()).thenReturn(List.of(umsatz));

        assertThat(provider.judge("42")).isEqualTo(VALID);
        // the name reads better in a rule and would never match - hence refused rather than noted
        assertThat(provider.judge("Umsatz je Auftrag")).isEqualTo(MALFORMED);
        assertThat(provider.judge("4711")).isEqualTo(UNKNOWN);
    }

    /** The id offered here has to be the value the checking site compares against, or the rule never fires. */
    @Test
    void theCheckingSiteAsksWithTheSameId() {
        var umsatz = report(42L, "Umsatz je Auftrag");
        when(reportService.getReportDefinitions()).thenReturn(List.of(umsatz));
        var authorizedUser = mock(AuthorizedUser.class);
        var authService = mock(AuthService.class);

        new ReportAuthorization(authorizedUser, authService).isAuthorized(umsatz, EXECUTE);

        var objectIds = ArgumentCaptor.forClass(String[].class);
        verify(authService).isAuthorized(anyString(), any(), any(), objectIds.capture());
        assertThat(objectIds.getValue())
            .containsExactlyElementsOf(provider.objects().stream().map(AuthorizationObject::id).toList());
    }

    /** Mocked rather than built: the id of an {@code AuditedEntity} is assigned by the database, not by a setter. */
    private ReportDefinition report(long id, String name) {
        var report = mock(ReportDefinition.class);
        when(report.getId()).thenReturn(id);
        when(report.getName()).thenReturn(name);
        return report;
    }

}
