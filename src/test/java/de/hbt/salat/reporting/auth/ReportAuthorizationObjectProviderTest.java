package de.hbt.salat.reporting.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
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
import de.hbt.salat.auth.domain.AuthorizationObject;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.reporting.domain.ReportDefinition;
import de.hbt.salat.reporting.domain.ReportGroupInfo;
import de.hbt.salat.reporting.service.ReportService;
import de.hbt.salat.reporting.service.ReportService.GroupedReport;
import org.springframework.context.support.MessageSourceAccessor;

@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = LENIENT)
class ReportAuthorizationObjectProviderTest {

    @Mock
    private ReportService reportService;

    @Mock
    private MessageSourceAccessor messages;

    @InjectMocks
    private ReportAuthorizationObjectProvider provider;

    @Test
    void theOfferedIdIsTheDatabaseIdAndTheLabelIsTheName() {
        var umsatz = report(42L, "Umsatz je Auftrag");
        when(reportService.getGroupedReportDefinitions()).thenReturn(List.of(grouped(umsatz, CONTROLLING)));

        assertThat(provider.objects())
            .containsExactly(new AuthorizationObject("42", "Umsatz je Auftrag", null, "Controlling"));
    }

    /** Titles repeat across groups (#1523): the editor lists the reports under their group, those without one too. */
    @Test
    void eachReportComesWithItsGroup() {
        when(messages.getMessage("main.reporting.group.none")).thenReturn("Ohne Gruppe");
        var grouped = grouped(report(42L, "Umsatz"), CONTROLLING);
        var loose = grouped(report(43L, "Umsatz"), ReportGroupInfo.NONE);
        when(reportService.getGroupedReportDefinitions()).thenReturn(List.of(grouped, loose));

        assertThat(provider.objects()).extracting(AuthorizationObject::label, AuthorizationObject::group)
            .containsExactly(tuple("Umsatz", "Controlling"), tuple("Umsatz", "Ohne Gruppe"));
    }

    private static final ReportGroupInfo CONTROLLING = new ReportGroupInfo(1L, "Controlling", false);

    private static GroupedReport grouped(ReportDefinition report, ReportGroupInfo group) {
        return new GroupedReport(report, group);
    }

    @Test
    void somethingThatIsNotANumberCannotBeAReportId() {
        var umsatz = report(42L, "Umsatz je Auftrag");
        when(reportService.getGroupedReportDefinitions()).thenReturn(List.of(grouped(umsatz, CONTROLLING)));

        assertThat(provider.judge("42")).isEqualTo(VALID);
        // the name reads better in a rule and would never match - hence refused rather than noted
        assertThat(provider.judge("Umsatz je Auftrag")).isEqualTo(MALFORMED);
        assertThat(provider.judge("4711")).isEqualTo(UNKNOWN);
    }

    /** The id offered here has to be the value the checking site compares against, or the rule never fires. */
    @Test
    void theCheckingSiteAsksWithTheSameId() {
        var umsatz = report(42L, "Umsatz je Auftrag");
        when(reportService.getGroupedReportDefinitions()).thenReturn(List.of(grouped(umsatz, CONTROLLING)));
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
