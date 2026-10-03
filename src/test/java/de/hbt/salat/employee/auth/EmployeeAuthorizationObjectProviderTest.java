package de.hbt.salat.employee.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.auth.domain.AccessLevel.LOGIN;
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
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeeService;

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

    /** The id of the login, not its name and not the sign: both can change and be given to somebody else (#1204). */
    @Test
    void theOfferedIdIsTheIdOfTheLogin() {
        var somebody = employee(31L, "l.muster", "mus");
        when(employeeService.getSelectableEmployees(null)).thenReturn(List.of(somebody));
        when(employeeService.getEmployeesBySalatUserIds(List.of(31L))).thenReturn(List.of(somebody));

        assertThat(provider.objects()).extracting(AuthorizationObject::id).containsExactly("31");
        assertThat(provider.judge("31")).isEqualTo(VALID);
        // a login since deleted leaves its id behind - noted, not refused
        assertThat(provider.judge("32")).isEqualTo(UNKNOWN);
        // a name is no id
        assertThat(provider.judge("l.muster")).isEqualTo(MALFORMED);
    }

    /** A hidden person is no longer offered, but a rule that names them still shows who it is. */
    @Test
    void aStoredLoginIsDescribedEvenWhenItsPersonIsHidden() {
        var hidden = employee(33L, "l.gegangen", "geg");
        when(hidden.getName()).thenReturn("Gina Gegangen");
        when(employeeService.getEmployeesBySalatUserIds(List.of(33L))).thenReturn(List.of(hidden));

        assertThat(provider.describe(List.of("33", "?alt"))).containsOnlyKeys("33")
            .containsEntry("33", new AuthorizationObject("33", "Gina Gegangen | geg", "l.gegangen"));
    }

    /**
     * Named like the person in every other select (#1266) — the stored id is not what one recognises —, and once
     * picked by the login name.
     */
    @Test
    void theOfferedPersonIsNamedByNameAndSign() {
        var somebody = employee(31L, "l.muster", "mus");
        when(somebody.getName()).thenReturn("Lea Muster");
        when(employeeService.getSelectableEmployees(null)).thenReturn(List.of(somebody));

        assertThat(provider.objects()).containsExactly(new AuthorizationObject("31", "Lea Muster | mus", "l.muster"));
    }

    /**
     * The id offered here has to be the value the checking site compares against. Taking over a login is the one
     * thing a rule can grant that grants everything else, so a mismatch here is worth its own test.
     */
    @Test
    void theCheckingSiteAsksWithTheSameId() {
        var somebody = employee(31L, "l.muster", "mus");
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

    private Employee employee(long salatUserId, String loginname, String sign) {
        var employee = mock(Employee.class);
        var salatUser = mock(SalatUser.class);
        when(salatUser.getId()).thenReturn(salatUserId);
        when(salatUser.getLoginname()).thenReturn(loginname);
        when(employee.getSalatUser()).thenReturn(salatUser);
        when(employee.getLoginname()).thenReturn(loginname);
        when(employee.getSign()).thenReturn(sign);
        return employee;
    }

}
