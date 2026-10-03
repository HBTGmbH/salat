package de.hbt.salat.dailyreport.auth;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.auth.domain.AccessLevel.READ;
import static de.hbt.salat.auth.domain.ObjectJudgement.MALFORMED;
import static de.hbt.salat.auth.domain.ObjectJudgement.UNKNOWN;
import static de.hbt.salat.auth.domain.ObjectJudgement.VALID;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * Typed as signs, stored as ids (#1204): the editor's free-text field for {@code TIMEREPORT}.
 */
@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = LENIENT)
class TimereportAuthorizationObjectProviderTest {

    private static final String EMPLOYEE_SIGN = "xx";
    private static final long EMPLOYEE_ID = 12L;
    private static final String CUSTOMER_ORDER_SIGN = "1453";
    private static final long CUSTOMER_ORDER_ID = 42L;
    private static final String SUBORDER_SIGN = "1453/01";
    private static final long SUBORDER_ID = 77L;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private CustomerorderService customerorderService;

    @Mock
    private SuborderService suborderService;

    @InjectMocks
    private TimereportAuthorizationObjectProvider provider;

    @BeforeEach
    void setUp() {
        var employee = mock(Employee.class);
        when(employee.getId()).thenReturn(EMPLOYEE_ID);
        when(employee.getSign()).thenReturn(EMPLOYEE_SIGN);
        when(employeeService.getEmployeeBySign(EMPLOYEE_SIGN)).thenReturn(employee);
        when(employeeService.getEmployeeById(EMPLOYEE_ID)).thenReturn(employee);

        var customerorder = mock(Customerorder.class);
        when(customerorder.getId()).thenReturn(CUSTOMER_ORDER_ID);
        when(customerorder.getSign()).thenReturn(CUSTOMER_ORDER_SIGN);
        when(customerorderService.getCustomerorderBySign(anyString())).thenReturn(null);
        when(customerorderService.getCustomerorderBySign(CUSTOMER_ORDER_SIGN)).thenReturn(customerorder);
        when(customerorderService.getCustomerorderById(anyLong())).thenReturn(null);
        when(customerorderService.getCustomerorderById(CUSTOMER_ORDER_ID)).thenReturn(customerorder);

        var suborder = mock(Suborder.class);
        when(suborder.getId()).thenReturn(SUBORDER_ID);
        when(suborder.getCompleteOrderSign()).thenReturn(SUBORDER_SIGN);
        when(suborderService.getSuborderByCompleteOrderSign(SUBORDER_SIGN)).thenReturn(suborder);
        when(suborderService.getSuborderById(SUBORDER_ID)).thenReturn(suborder);
    }

    @Test
    void theCategoryCannotBeEnumerated() {
        assertThat(provider.objects()).isEmpty();
    }

    @Test
    void theTypedFormsAreStoredAsIds() {
        assertThat(provider.objectIdOf(CUSTOMER_ORDER_SIGN)).contains("C42");
        assertThat(provider.objectIdOf(SUBORDER_SIGN)).contains("S77");
        assertThat(provider.objectIdOf(EMPLOYEE_SIGN + ":" + CUSTOMER_ORDER_SIGN)).contains("E12:C42");
        assertThat(provider.objectIdOf(EMPLOYEE_SIGN + ":" + SUBORDER_SIGN)).contains("E12:S77");
        // the wildcard only this category knows - without it the form #1089 introduced stays out of reach
        assertThat(provider.objectIdOf(EMPLOYEE_SIGN + ":*")).contains("E12:*");
    }

    /** An id cannot precede the record it names: what does not resolve is not stored. */
    @Test
    void whatNamesNoRecordYieldsNothing() {
        assertThat(provider.objectIdOf("yy:" + CUSTOMER_ORDER_SIGN)).isEmpty();
        assertThat(provider.objectIdOf("4711")).isEmpty();
        assertThat(provider.objectIdOf(EMPLOYEE_SIGN + ":4711")).isEmpty();
        assertThat(provider.objectIdOf("1453/99")).isEmpty();
        assertThat(provider.objectIdOf(EMPLOYEE_SIGN + ":")).isEmpty();
    }

    /** The editor shows a stored object in the form it is typed, with the sign and order sign it has today. */
    @Test
    void aStoredObjectIsShownInTheTypedForm() {
        assertThat(provider.describe(List.of("C42", "E12:S77", "E12:*", "E99:C42", "?xx:alt")))
            .containsOnlyKeys("C42", "E12:S77", "E12:*")
            .containsEntry("C42", new AuthorizationObject("C42", CUSTOMER_ORDER_SIGN))
            .containsEntry("E12:S77", new AuthorizationObject("E12:S77", EMPLOYEE_SIGN + ":" + SUBORDER_SIGN))
            .containsEntry("E12:*", new AuthorizationObject("E12:*", EMPLOYEE_SIGN + ":*"));
    }

    @Test
    void aStoredObjectIsJudgedByItsFormAndByWhetherItsRecordsStillExist() {
        assertThat(provider.judge("E12:C42")).isEqualTo(VALID);
        assertThat(provider.judge("S77")).isEqualTo(VALID);
        assertThat(provider.judge("E99:C42")).isEqualTo(UNKNOWN);
        assertThat(provider.judge("C4711")).isEqualTo(UNKNOWN);
        assertThat(provider.judge(EMPLOYEE_SIGN + ":" + CUSTOMER_ORDER_SIGN)).isEqualTo(MALFORMED);
        assertThat(provider.judge("1453")).isEqualTo(MALFORMED);
    }

    /**
     * The point of the whole exercise: what the editor stores has to be what the checking site asks with. Both sides
     * are asked here rather than compared by eye — a letter moved on one side and not the other would leave a rule that
     * looks right and never fires.
     */
    @Test
    void theCheckingSiteAsksWithEveryFormTheEditorStores() {
        var authorizedUser = mock(AuthorizedUser.class);
        var authService = mock(AuthService.class);
        var timereport = mock(Timereport.class, RETURNS_DEEP_STUBS);
        when(authorizedUser.getEffectiveLoginSign()).thenReturn("reader");
        when(timereport.getEmployeecontract().getEmployee().getId()).thenReturn(EMPLOYEE_ID);
        when(timereport.getEmployeecontract().getEmployee().getSalatUser().getLoginname()).thenReturn("somebody-else");
        when(timereport.getSuborder().getId()).thenReturn(SUBORDER_ID);
        when(timereport.getSuborder().getCustomerorder().getId()).thenReturn(CUSTOMER_ORDER_ID);
        when(timereport.getSuborder().getCustomerorder().getResponsibleHbt()).thenReturn(List.of());
        when(timereport.getReferenceday().getRefdate()).thenReturn(of(2011, 1, 2));

        new TimereportAuthorization(authorizedUser, authService, mock(EmployeecontractService.class)).isAuthorized(timereport, READ);

        // a captor of the array type takes the whole vararg list, not just a single value
        var objectIds = ArgumentCaptor.forClass(String[].class);
        verify(authService).isAuthorized(anyString(), any(), any(), objectIds.capture());
        var typed = List.of(CUSTOMER_ORDER_SIGN, SUBORDER_SIGN, EMPLOYEE_SIGN + ":" + CUSTOMER_ORDER_SIGN,
            EMPLOYEE_SIGN + ":" + SUBORDER_SIGN, EMPLOYEE_SIGN + ":*");
        assertThat(objectIds.getValue())
            .as("the checking site asks with exactly the ids the editor stores for the typed forms")
            .containsExactlyInAnyOrderElementsOf(typed.stream().map(provider::objectIdOf).map(Optional::orElseThrow).toList());
    }

}
