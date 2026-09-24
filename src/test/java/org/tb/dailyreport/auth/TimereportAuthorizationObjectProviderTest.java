package org.tb.dailyreport.auth;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static org.tb.auth.domain.AccessLevel.READ;
import static org.tb.auth.domain.ObjectJudgement.MALFORMED;
import static org.tb.auth.domain.ObjectJudgement.UNKNOWN;
import static org.tb.auth.domain.ObjectJudgement.VALID;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.service.EmployeeService;
import org.tb.order.domain.Customerorder;
import org.tb.order.service.CustomerorderService;

@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = LENIENT)
class TimereportAuthorizationObjectProviderTest {

    private static final String EMPLOYEE_SIGN = "xx";
    private static final String CUSTOMER_ORDER_SIGN = "1453";
    private static final String SUBORDER_SIGN = "1453/01";

    @Mock
    private EmployeeService employeeService;

    @Mock
    private CustomerorderService customerorderService;

    @InjectMocks
    private TimereportAuthorizationObjectProvider provider;

    @BeforeEach
    void setUp() {
        when(employeeService.getAllEmployeeSigns()).thenReturn(Set.of(EMPLOYEE_SIGN));
        when(customerorderService.getCustomerorderBySign(anyString())).thenReturn(null);
        when(customerorderService.getCustomerorderBySign(CUSTOMER_ORDER_SIGN)).thenReturn(new Customerorder());
    }

    @Test
    void theCategoryCannotBeEnumerated() {
        assertThat(provider.objects()).isEmpty();
    }

    @Test
    void theFormsOfThisCategoryPass() {
        assertThat(provider.judge(CUSTOMER_ORDER_SIGN)).isEqualTo(VALID);
        assertThat(provider.judge(SUBORDER_SIGN)).isEqualTo(VALID);
        assertThat(provider.judge(EMPLOYEE_SIGN + ":" + CUSTOMER_ORDER_SIGN)).isEqualTo(VALID);
        assertThat(provider.judge(EMPLOYEE_SIGN + ":" + SUBORDER_SIGN)).isEqualTo(VALID);
        // the wildcard only this category knows - without it the form #1089 introduced stays out of reach
        assertThat(provider.judge(EMPLOYEE_SIGN + ":*")).isEqualTo(VALID);
    }

    @Test
    void anEmptyPartIsMalformed() {
        assertThat(provider.judge(EMPLOYEE_SIGN + ":")).isEqualTo(MALFORMED);
        assertThat(provider.judge(":" + CUSTOMER_ORDER_SIGN)).isEqualTo(MALFORMED);
    }

    @Test
    void anUnknownSignOrOrderIsNotedRatherThanRefused() {
        assertThat(provider.judge("yy:" + CUSTOMER_ORDER_SIGN)).isEqualTo(UNKNOWN);
        assertThat(provider.judge("4711")).isEqualTo(UNKNOWN);
        assertThat(provider.judge(EMPLOYEE_SIGN + ":4711")).isEqualTo(UNKNOWN);
    }

    /**
     * The point of the whole exercise: what the editor lets through has to be what the checking site asks with. Both
     * sides are asked here rather than compared by eye — a colon moved on one side and not the other would leave a
     * rule that looks right and never fires.
     */
    @Test
    void everyFormTheCheckingSiteAsksWithIsAcceptedHere() {
        var authorizedUser = mock(AuthorizedUser.class);
        var authService = mock(AuthService.class);
        var timereport = mock(Timereport.class, RETURNS_DEEP_STUBS);
        when(authorizedUser.getEffectiveLoginSign()).thenReturn("reader");
        when(timereport.getEmployeecontract().getEmployee().getSign()).thenReturn(EMPLOYEE_SIGN);
        when(timereport.getEmployeecontract().getEmployee().getSalatUser().getLoginname()).thenReturn("somebody-else");
        when(timereport.getSuborder().getCustomerorder().getSign()).thenReturn(CUSTOMER_ORDER_SIGN);
        when(timereport.getSuborder().getCompleteOrderSign()).thenReturn(SUBORDER_SIGN);
        when(timereport.getSuborder().getCustomerorder().getResponsibleHbt()).thenReturn(List.of());
        when(timereport.getReferenceday().getRefdate()).thenReturn(of(2011, 1, 2));

        new TimereportAuthorization(authorizedUser, authService).isAuthorized(timereport, READ);

        // a captor of the array type takes the whole vararg list, not just a single value
        var objectIds = ArgumentCaptor.forClass(String[].class);
        verify(authService).isAuthorized(anyString(), any(), any(), objectIds.capture());
        assertThat(objectIds.getValue())
            .as("every object the checking site asks with must be a form the editor accepts")
            .isNotEmpty()
            .allSatisfy(objectId -> assertThat(provider.judge(objectId)).isNotEqualTo(MALFORMED));
    }

}
