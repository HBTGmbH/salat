package org.tb.employee.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.tb.common.exception.ErrorCode.EC_NO_CURRENT_CONTRACT;
import static org.tb.common.exception.ErrorCode.EM_NO_LOGIN_EMPLOYEE;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.event.AuthorizedUserChangedEvent;
import org.tb.common.GlobalConstants;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.EmployeeAccessDenial;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.event.EmployeecontractChangedEvent;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.testutils.EmployeeTestUtils;
import org.tb.testutils.EmployeecontractTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthorizedUserChangedListenerTest {

  @InjectMocks
  private AuthorizedUserChangedListener classUnderTest;
  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private AuthorizedUser authorizedUser;
  @Mock
  private EmployeeService employeeService;
  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private EmployeeAccessDenial employeeAccessDenial;

  @Test
  void publishes_employeecontract_changed_event_when_valid_contract_present() {
    Employee loginEmployee = EmployeeTestUtils.createEmployee("testy");
    setField(loginEmployee, "id", 42L);
    Employeecontract employeecontract = EmployeecontractTestUtils.createEmployeecontract(loginEmployee, null);
    setField(employeecontract, "id", 123L);

    when(employeeService.getLoginEmployee()).thenReturn(loginEmployee);
    when(employeecontractService.getCurrentContract(42L)).thenReturn(Optional.of(employeecontract));

    classUnderTest.onAuthorizedUserChanged(new AuthorizedUserChangedEvent(this));

    ArgumentCaptor<EmployeecontractChangedEvent> captor = ArgumentCaptor.forClass(EmployeecontractChangedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().getEmployeecontractId()).isEqualTo(123L);
    verify(employeeAccessDenial, never()).deny(any());
  }

  @Test
  void does_not_publish_employeecontract_changed_event_when_no_contract_present() {
    Employee loginEmployee = EmployeeTestUtils.createEmployee("adm");
    setField(loginEmployee, "id", 1L);
    loginEmployee.getSalatUser().setStatus(GlobalConstants.EMPLOYEE_STATUS_ADM);

    when(employeeService.getLoginEmployee()).thenReturn(loginEmployee);
    when(employeecontractService.getCurrentContract(1L)).thenReturn(Optional.empty());

    classUnderTest.onAuthorizedUserChanged(new AuthorizedUserChangedEvent(this));

    verify(eventPublisher, never()).publishEvent(any());
    verify(employeeAccessDenial, never()).deny(any());
  }

  /**
   * #1054: der Hörer läuft bei jeder Authentifizierung und damit auch in der Weiterleitung auf
   * {@code /error}. Er hält die Bedingung deshalb nur fest, statt sie zu werfen.
   */
  @Test
  void records_denial_without_throwing_when_no_current_contract() {
    Employee loginEmployee = EmployeeTestUtils.createEmployee("testy");
    setField(loginEmployee, "id", 42L);

    when(employeeService.getLoginEmployee()).thenReturn(loginEmployee);
    when(employeecontractService.getCurrentContract(42L)).thenReturn(Optional.empty());

    assertThatCode(() -> classUnderTest.onAuthorizedUserChanged(new AuthorizedUserChangedEvent(this)))
        .doesNotThrowAnyException();

    ArgumentCaptor<ServiceFeedbackMessage> captor = ArgumentCaptor.forClass(ServiceFeedbackMessage.class);
    verify(employeeAccessDenial).deny(captor.capture());
    assertThat(captor.getValue().getErrorCode()).isEqualTo(EC_NO_CURRENT_CONTRACT);
    assertThat(captor.getValue().getArguments()).containsExactly("testy");
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void records_denial_without_throwing_when_no_matching_employee() {
    when(employeeService.getLoginEmployee()).thenReturn(null);
    when(authorizedUser.getEffectiveLoginSign()).thenReturn("nobody");

    assertThatCode(() -> classUnderTest.onAuthorizedUserChanged(new AuthorizedUserChangedEvent(this)))
        .doesNotThrowAnyException();

    ArgumentCaptor<ServiceFeedbackMessage> captor = ArgumentCaptor.forClass(ServiceFeedbackMessage.class);
    verify(employeeAccessDenial).deny(captor.capture());
    assertThat(captor.getValue().getErrorCode()).isEqualTo(EM_NO_LOGIN_EMPLOYEE);
    assertThat(captor.getValue().getArguments()).containsExactly("nobody");
    verify(eventPublisher, never()).publishEvent(any());
  }

}
