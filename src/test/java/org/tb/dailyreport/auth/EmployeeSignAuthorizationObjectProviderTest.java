package org.tb.dailyreport.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.tb.auth.domain.AccessLevel.WRITE;
import static org.tb.auth.domain.ObjectJudgement.UNKNOWN;
import static org.tb.auth.domain.ObjectJudgement.VALID;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.tb.auth.domain.AuthorizationObject;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;

/**
 * The three categories of this module whose object is the person: releases, acceptances and working days. They share
 * their objects, and the id is the <em>sign</em> — unlike {@code EMPLOYEE}, which carries the login name.
 */
@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = LENIENT)
class EmployeeSignAuthorizationObjectProviderTest {

    private static final String SIGN = "mus";

    @Mock
    private EmployeeService employeeService;

    private ReleaseAuthorizationObjectProvider release;
    private AcceptAuthorizationObjectProvider accept;
    private WorkingdayAuthorizationObjectProvider workingday;

    @BeforeEach
    void setUp() {
        var muster = mock(Employee.class);
        when(muster.getSign()).thenReturn(SIGN);
        when(employeeService.getSelectableEmployees(null)).thenReturn(List.of(muster));
        release = new ReleaseAuthorizationObjectProvider(employeeService);
        accept = new AcceptAuthorizationObjectProvider(employeeService);
        workingday = new WorkingdayAuthorizationObjectProvider(employeeService);
    }

    @Test
    void hiddenPeopleAreNotOffered() {
        // the select box method, not getAllEmployeeSigns - that one answers whether a stored sign resolves
        when(employeeService.getSelectableEmployees(null)).thenReturn(List.of());

        assertThat(release.objects()).isEmpty();
        verify(employeeService, never()).getAllEmployeeSigns();
    }

    @Test
    void theThreeCategoriesAreTheOnesTheCheckingSitesUse() {
        assertThat(release.category()).isEqualTo("RELEASE_TIMEREPORTS");
        assertThat(accept.category()).isEqualTo("ACCEPT_TIMEREPORTS");
        assertThat(workingday.category()).isEqualTo("WORKINGDAY");
    }

    @Test
    void allThreeOfferTheEmployeeSigns() {
        assertThat(release.objects()).containsExactly(new AuthorizationObject(SIGN, SIGN));
        assertThat(accept.objects()).isEqualTo(release.objects());
        assertThat(workingday.objects()).isEqualTo(release.objects());
        assertThat(release.judge(SIGN)).isEqualTo(VALID);
        assertThat(release.judge("nobody")).isEqualTo(UNKNOWN);
    }

    /** The id offered here has to be the value the checking site compares against, or the rule never fires. */
    @Test
    void theCheckingSiteAsksWithTheSameId() {
        var authorizedUser = mock(AuthorizedUser.class);
        var authService = mock(AuthService.class);
        var contract = mock(Employeecontract.class, Answers.RETURNS_DEEP_STUBS);
        when(authorizedUser.getEffectiveLoginSign()).thenReturn("somebody-else");
        when(contract.getEmployee().getSign()).thenReturn(SIGN);
        when(contract.getEmployee().getSalatUser().getLoginname()).thenReturn("l.muster");
        when(contract.getSupervisors()).thenReturn(List.of());

        new ReleaseAuthorization(authorizedUser, authService).isReleaseAuthorized(contract, WRITE);

        var objectIds = ArgumentCaptor.forClass(String[].class);
        verify(authService).isAuthorized(anyString(), any(), any(), objectIds.capture());
        assertThat(objectIds.getValue())
            .containsExactlyElementsOf(release.objects().stream().map(AuthorizationObject::id).toList());
    }

}
