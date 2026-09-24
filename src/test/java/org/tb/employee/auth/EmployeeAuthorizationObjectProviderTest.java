package org.tb.employee.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.tb.auth.domain.AccessLevel.LOGIN;
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
import org.tb.auth.domain.SalatUser;
import org.tb.auth.service.AuthService;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;

@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = LENIENT)
class EmployeeAuthorizationObjectProviderTest {

    @Mock
    private EmployeeService employeeService;

    @InjectMocks
    private EmployeeAuthorizationObjectProvider provider;

    @Test
    void hiddenPeopleAreNotOffered() {
        // getSelectableEmployees is the select box method and leaves the hidden out; getAllEmployees would not say so
        when(employeeService.getSelectableEmployees(null)).thenReturn(List.of());

        assertThat(provider.objects()).isEmpty();
        verify(employeeService, never()).getAllEmployees();
    }

    @Test
    void theOfferedIdIsTheLoginNameAndNotTheSign() {
        var somebody = employee("l.muster", "mus");
        when(employeeService.getSelectableEmployees(null)).thenReturn(List.of(somebody));

        assertThat(provider.objects()).extracting(AuthorizationObject::id).containsExactly("l.muster");
        assertThat(provider.judge("l.muster")).isEqualTo(VALID);
        // the sign looks like an answer and would never match - noted, because it may also be a login not yet created
        assertThat(provider.judge("mus")).isEqualTo(UNKNOWN);
    }

    /**
     * The id offered here has to be the value the checking site compares against. Taking over a login is the one
     * thing a rule can grant that grants everything else, so a mismatch here is worth its own test.
     */
    @Test
    void theCheckingSiteAsksWithTheSameId() {
        var somebody = employee("l.muster", "mus");
        when(employeeService.getSelectableEmployees(null)).thenReturn(List.of(somebody));
        var authorizedUser = mock(AuthorizedUser.class);
        var authService = mock(AuthService.class);
        when(authorizedUser.getLoginSign()).thenReturn("somebody-else");

        new EmployeeAuthorization(authService, authorizedUser).isAuthorized(somebody, LOGIN);

        var objectIds = ArgumentCaptor.forClass(String[].class);
        verify(authService).isAuthorizedForOwnLogin(anyString(), any(), any(), objectIds.capture());
        assertThat(objectIds.getValue())
            .containsExactlyElementsOf(provider.objects().stream().map(AuthorizationObject::id).toList());
    }

    private Employee employee(String loginname, String sign) {
        var employee = mock(Employee.class);
        var salatUser = mock(SalatUser.class);
        when(salatUser.getLoginname()).thenReturn(loginname);
        when(employee.getSalatUser()).thenReturn(salatUser);
        when(employee.getLoginname()).thenReturn(loginname);
        when(employee.getSign()).thenReturn(sign);
        return employee;
    }

}
